/*  Title:      Pure/General/ssh.scala
    Author:     Makarius

SSH client based on JSch (see also http://www.jcraft.com/jsch/examples).
*/

package isabelle


import java.io.{InputStream, OutputStream, ByteArrayOutputStream}

import scala.collection.{mutable, JavaConversions}

import com.jcraft.jsch.{JSch, Logger => JSch_Logger, Session => JSch_Session, SftpException,
  OpenSSHConfig, UserInfo, Channel => JSch_Channel, ChannelExec, ChannelSftp, SftpATTRS}


object SSH
{
  /* target machine: user@host syntax */

  object Target
  {
    val Pattern = "^([^@]+)@(.+)$".r

    def parse(s: String): (String, String) =
      s match {
        case Pattern(user, host) => (user, host)
        case _ => ("", s)
      }

    def unapplySeq(s: String): Option[List[String]] =
      parse(s) match {
        case (_, "") => None
        case (user, host) => Some(List(user, host))
      }
  }

  val default_port = 22
  def make_port(port: Int): Int = if (port > 0) port else default_port

  def connect_timeout(options: Options): Int =
    options.seconds("ssh_connect_timeout").ms.toInt

  def alive_interval(options: Options): Int =
    options.seconds("ssh_alive_interval").ms.toInt

  def alive_count_max(options: Options): Int =
    options.int("ssh_alive_count_max")


  /* init context */

  def init_context(options: Options): Context =
  {
    val config_dir = Path.explode(options.string("ssh_config_dir"))
    if (!config_dir.is_dir) error("Bad ssh config directory: " + config_dir)

    val jsch = new JSch

    val config_file = Path.explode(options.string("ssh_config_file"))
    if (config_file.is_file)
      jsch.setConfigRepository(OpenSSHConfig.parseFile(File.platform_path(config_file)))

    val known_hosts = config_dir + Path.explode("known_hosts")
    if (!known_hosts.is_file) known_hosts.file.createNewFile
    jsch.setKnownHosts(File.platform_path(known_hosts))

    val identity_files =
      space_explode(':', options.string("ssh_identity_files")).map(Path.explode(_))
    for (identity_file <- identity_files if identity_file.is_file)
      jsch.addIdentity(File.platform_path(identity_file))

    new Context(options, jsch)
  }

  def open_session(options: Options, host: String, user: String = "", port: Int = 0,
      proxy_host: String = "", proxy_user: String = "", proxy_port: Int = 0,
      permissive: Boolean = false): Session =
    init_context(options).open_session(host = host, user = user, port = port,
      proxy_host = proxy_host, proxy_user = proxy_user, proxy_port = proxy_port,
      permissive = permissive)

  class Context private[SSH](val options: Options, val jsch: JSch)
  {
    def update_options(new_options: Options): Context = new Context(new_options, jsch)

    def connect_session(host: String, user: String = "", port: Int = 0,
      host_key_permissive: Boolean = false,
      host_key_alias: String = "",
      on_close: () => Unit = () => ()): Session =
    {
      val session = jsch.getSession(proper_string(user) getOrElse null, host, make_port(port))

      session.setUserInfo(No_User_Info)
      session.setServerAliveInterval(alive_interval(options))
      session.setServerAliveCountMax(alive_count_max(options))
      session.setConfig("MaxAuthTries", "3")
      if (host_key_permissive) session.setConfig("StrictHostKeyChecking", "no")
      if (host_key_alias != "") session.setHostKeyAlias(host_key_alias)

      if (options.bool("ssh_compression")) {
        session.setConfig("compression.s2c", "zlib@openssh.com,zlib,none")
        session.setConfig("compression.c2s", "zlib@openssh.com,zlib,none")
        session.setConfig("compression_level", "9")
      }
      session.connect(connect_timeout(options))
      new Session(options, session, on_close)
    }

    def open_session(host: String, user: String = "", port: Int = 0,
      proxy_host: String = "", proxy_user: String = "", proxy_port: Int = 0,
      permissive: Boolean = false): Session =
    {
      if (proxy_host == "") connect_session(host = host, user = user, port = port)
      else {
        val proxy = connect_session(host = proxy_host, port = proxy_port, user = proxy_user)

        val fw =
          try { proxy.port_forwarding(remote_host = host, remote_port = make_port(port)) }
          catch { case exn: Throwable => proxy.close; throw exn }

        try {
          connect_session(host = fw.local_host, port = fw.local_port,
            host_key_permissive = permissive, host_key_alias = host,
            user = user, on_close = () => { fw.close; proxy.close })
        }
        catch { case exn: Throwable => fw.close; proxy.close; throw exn }
      }
    }
  }


  /* logging */

  def logging(verbose: Boolean = true, debug: Boolean = false)
  {
    JSch.setLogger(if (verbose) new Logger(debug) else null)
  }

  private class Logger(debug: Boolean) extends JSch_Logger
  {
    def isEnabled(level: Int): Boolean = level != JSch_Logger.DEBUG || debug

    def log(level: Int, msg: String)
    {
      level match {
        case JSch_Logger.ERROR | JSch_Logger.FATAL => Output.error_message(msg)
        case JSch_Logger.WARN => Output.warning(msg)
        case _ => Output.writeln(msg)
      }
    }
  }


  /* user info */

  object No_User_Info extends UserInfo
  {
    def getPassphrase: String = null
    def getPassword: String = null
    def promptPassword(msg: String): Boolean = false
    def promptPassphrase(msg: String): Boolean = false
    def promptYesNo(msg: String): Boolean = false
    def showMessage(msg: String): Unit = Output.writeln(msg)
  }


  /* port forwarding */

  object Port_Forwarding
  {
    def open(ssh: Session, ssh_close: Boolean,
      local_host: String, local_port: Int, remote_host: String, remote_port: Int): Port_Forwarding =
    {
      val port = ssh.session.setPortForwardingL(local_host, local_port, remote_host, remote_port)
      new Port_Forwarding(ssh, ssh_close, local_host, port, remote_host, remote_port)
    }
  }

  class Port_Forwarding private[SSH](
    ssh: SSH.Session,
    ssh_close: Boolean,
    val local_host: String,
    val local_port: Int,
    val remote_host: String,
    val remote_port: Int) extends AutoCloseable
  {
    override def toString: String =
      local_host + ":" + local_port + ":" + remote_host + ":" + remote_port

    def close()
    {
      ssh.session.delPortForwardingL(local_host, local_port)
      if (ssh_close) ssh.close()
    }
  }


  /* Sftp channel */

  type Attrs = SftpATTRS

  sealed case class Dir_Entry(name: String, is_dir: Boolean)
  {
    def is_file: Boolean = !is_dir
  }


  /* exec channel */

  private val exec_wait_delay = Time.seconds(0.3)

  class Exec private[SSH](session: Session, channel: ChannelExec) extends AutoCloseable
  {
    override def toString: String = "exec " + session.toString

    def close() { channel.disconnect }

    val exit_status: Future[Int] =
      Future.thread("ssh_wait") {
        while (!channel.isClosed) Thread.sleep(exec_wait_delay.ms)
        channel.getExitStatus
      }

    val stdin: OutputStream = channel.getOutputStream
    val stdout: InputStream = channel.getInputStream
    val stderr: InputStream = channel.getErrStream

    // connect after preparing streams
    channel.connect(connect_timeout(session.options))

    def result(
      progress_stdout: String => Unit = (_: String) => (),
      progress_stderr: String => Unit = (_: String) => (),
      strict: Boolean = true): Process_Result =
    {
      stdin.close

      def read_lines(stream: InputStream, progress: String => Unit): List[String] =
      {
        val result = new mutable.ListBuffer[String]
        val line_buffer = new ByteArrayOutputStream(100)
        def line_flush()
        {
          val line = Library.trim_line(line_buffer.toString(UTF8.charset_name))
          progress(line)
          result += line
          line_buffer.reset
        }

        var c = 0
        var finished = false
        while (!finished) {
          while ({ c = stream.read; c != -1 && c != 10 }) line_buffer.write(c)
          if (c == 10) line_flush()
          else if (channel.isClosed) {
            if (line_buffer.size > 0) line_flush()
            finished = true
          }
          else Thread.sleep(exec_wait_delay.ms)
        }

        result.toList
      }

      val out_lines = Future.thread("ssh_stdout") { read_lines(stdout, progress_stdout) }
      val err_lines = Future.thread("ssh_stderr") { read_lines(stderr, progress_stderr) }

      def terminate()
      {
        close
        out_lines.join
        err_lines.join
        exit_status.join
      }

      val rc =
        try { exit_status.join }
        catch { case Exn.Interrupt() => terminate(); Exn.Interrupt.return_code }

      close
      if (strict && rc == Exn.Interrupt.return_code) throw Exn.Interrupt()

      Process_Result(rc, out_lines.join, err_lines.join)
    }
  }


  /* session */

  class Session private[SSH](
    val options: Options,
    val session: JSch_Session,
    on_close: () => Unit) extends System with AutoCloseable
  {
    def update_options(new_options: Options): Session =
      new Session(new_options, session, on_close)

    def user_prefix: String = if (session.getUserName == null) "" else session.getUserName + "@"
    def host: String = if (session.getHost == null) "" else session.getHost
    def port_suffix: String = if (session.getPort == default_port) "" else ":" + session.getPort
    override def hg_url: String = "ssh://" + user_prefix + host + port_suffix + "/"

    override def prefix: String =
      user_prefix + host + port_suffix + ":"

    override def toString: String =
      user_prefix + host + port_suffix + (if (session.isConnected) "" else " (disconnected)")


    /* port forwarding */

    def port_forwarding(
        remote_port: Int, remote_host: String = "localhost",
        local_port: Int = 0, local_host: String = "localhost",
        ssh_close: Boolean = false): Port_Forwarding =
      Port_Forwarding.open(this, ssh_close, local_host, local_port, remote_host, remote_port)


    /* sftp channel */

    val sftp: ChannelSftp = session.openChannel("sftp").asInstanceOf[ChannelSftp]
    sftp.connect(connect_timeout(options))

    def close() { sftp.disconnect; session.disconnect; on_close() }

    val settings: Map[String, String] =
    {
      val home = sftp.getHome
      Map("HOME" -> home, "USER_HOME" -> home)
    }
    override def expand_path(path: Path): Path = path.expand_env(settings)
    def remote_path(path: Path): String = expand_path(path).implode
    override def bash_path(path: Path): String = Bash.string(remote_path(path))

    def chmod(permissions: Int, path: Path): Unit = sftp.chmod(permissions, remote_path(path))
    def mv(path1: Path, path2: Path): Unit = sftp.rename(remote_path(path1), remote_path(path2))
    def rm(path: Path): Unit = sftp.rm(remote_path(path))
    def mkdir(path: Path): Unit = sftp.mkdir(remote_path(path))
    def rmdir(path: Path): Unit = sftp.rmdir(remote_path(path))

    private def test_entry(path: Path, as_dir: Boolean): Boolean =
      try {
        val is_dir = sftp.stat(remote_path(path)).isDir
        if (as_dir) is_dir else !is_dir
      }
      catch { case _: SftpException => false }

    override def is_dir(path: Path): Boolean = test_entry(path, true)
    override def is_file(path: Path): Boolean = test_entry(path, false)

    def is_link(path: Path): Boolean =
      try { sftp.lstat(remote_path(path)).isLink }
      catch { case _: SftpException => false }

    override def mkdirs(path: Path): Unit =
      if (!is_dir(path)) {
        execute(
          "perl -e \"use File::Path make_path; make_path('" + remote_path(path) + "');\"")
        if (!is_dir(path)) error("Failed to create directory: " + quote(remote_path(path)))
      }

    def read_dir(path: Path): List[Dir_Entry] =
    {
      if (!is_dir(path)) error("No such directory: " + path.toString)

      val dir_name = remote_path(path)
      val dir = sftp.ls(dir_name)
      (for {
        i <- (0 until dir.size).iterator
        a = dir.get(i).asInstanceOf[AnyRef]
        name = Untyped.get[String](a, "filename")
        attrs = Untyped.get[Attrs](a, "attrs")
        if name != "." && name != ".."
      }
      yield {
        Dir_Entry(name,
          if (attrs.isLink) {
            try { sftp.stat(dir_name + "/" + name).isDir }
            catch { case _: SftpException => false }
          }
          else attrs.isDir)
      }).toList.sortBy(_.name)
    }

    def find_files(
      start: Path,
      pred: Path => Boolean = _ => true,
      include_dirs: Boolean = false,
      follow_links: Boolean = false): List[Path] =
    {
      val result = new mutable.ListBuffer[Path]
      def check(path: Path) { if (pred(path)) result += path }

      def find(dir: Path)
      {
        if (include_dirs) check(dir)
        if (follow_links || !is_link(dir)) {
          for (entry <- read_dir(dir)) {
            val path = dir + Path.basic(entry.name)
            if (entry.is_file) check(path) else find(path)
          }
        }
      }
      if (is_file(start)) check(start) else find(start)

      result.toList
    }

    def open_input(path: Path): InputStream = sftp.get(remote_path(path))
    def open_output(path: Path): OutputStream = sftp.put(remote_path(path))

    def read_file(path: Path, local_path: Path): Unit =
      sftp.get(remote_path(path), File.platform_path(local_path))
    def read_bytes(path: Path): Bytes = using(open_input(path))(Bytes.read_stream(_))
    def read(path: Path): String = using(open_input(path))(File.read_stream(_))

    def write_file(path: Path, local_path: Path): Unit =
      sftp.put(File.platform_path(local_path), remote_path(path))
    def write_bytes(path: Path, bytes: Bytes): Unit =
      using(open_output(path))(bytes.write_stream(_))
    def write(path: Path, text: String): Unit =
      using(open_output(path))(stream => Bytes(text).write_stream(stream))


    /* exec channel */

    def exec(command: String): Exec =
    {
      val channel = session.openChannel("exec").asInstanceOf[ChannelExec]
      channel.setCommand("export USER_HOME=\"$HOME\"\n" + command)
      new Exec(this, channel)
    }

    override def execute(command: String,
        progress_stdout: String => Unit = (_: String) => (),
        progress_stderr: String => Unit = (_: String) => (),
        strict: Boolean = true): Process_Result =
      exec(command).result(progress_stdout, progress_stderr, strict)


    /* tmp dirs */

    def rm_tree(dir: Path): Unit = rm_tree(remote_path(dir))

    def rm_tree(remote_dir: String): Unit =
      execute("rm -r -f " + Bash.string(remote_dir)).check

    def tmp_dir(): String =
      execute("mktemp -d -t tmp.XXXXXXXXXX").check.out

    def with_tmp_dir[A](body: Path => A): A =
    {
      val remote_dir = tmp_dir()
      try { body(Path.explode(remote_dir)) } finally { rm_tree(remote_dir) }
    }
  }



  /* system operations */

  trait System
  {
    def hg_url: String = ""
    def prefix: String = ""

    def expand_path(path: Path): Path = path.expand
    def bash_path(path: Path): String = File.bash_path(path)
    def is_dir(path: Path): Boolean = path.is_dir
    def is_file(path: Path): Boolean = path.is_file
    def mkdirs(path: Path): Unit = Isabelle_System.mkdirs(path)

    def execute(command: String,
        progress_stdout: String => Unit = (_: String) => (),
        progress_stderr: String => Unit = (_: String) => (),
        strict: Boolean = true): Process_Result =
      Isabelle_System.bash(command, progress_stdout = progress_stdout,
        progress_stderr = progress_stderr, strict = strict)
  }

  object Local extends System
}
