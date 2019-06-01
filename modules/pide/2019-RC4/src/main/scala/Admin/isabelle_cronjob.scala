/*  Title:      Pure/Admin/isabelle_cronjob.scala
    Author:     Makarius

Main entry point for administrative cronjob at TUM.
*/

package isabelle


import java.nio.file.Files

import scala.annotation.tailrec
import scala.collection.mutable


object Isabelle_Cronjob
{
  /* global resources: owned by main cronjob */

  val backup = "lxbroy10:cronjob"
  val main_dir = Path.explode("~/cronjob")
  val main_state_file = main_dir + Path.explode("run/main.state")
  val current_log = main_dir + Path.explode("run/main.log")  // owned by log service
  val cumulative_log = main_dir + Path.explode("log/main.log")  // owned by log service

  val isabelle_repos_source = "https://isabelle.sketis.net/repos/isabelle"
  val isabelle_repos = main_dir + Path.explode("isabelle")
  val afp_repos = main_dir + Path.explode("AFP")

  val build_log_dirs =
    List(Path.explode("~/log"), Path.explode("~/afp/log"), Path.explode("~/cronjob/log"))



  /** logger tasks **/

  sealed case class Logger_Task(name: String = "", body: Logger => Unit)


  /* init and exit */

  def get_rev(): String = Mercurial.repository(isabelle_repos).id()
  def get_afp_rev(): String = Mercurial.repository(afp_repos).id()

  val init =
    Logger_Task("init", logger =>
      {
        Isabelle_Devel.make_index()

        Mercurial.setup_repository(isabelle_repos_source, isabelle_repos)
        Mercurial.setup_repository(AFP.repos_source, afp_repos)

        File.write(logger.log_dir + Build_Log.log_filename("isabelle_identify", logger.start_date),
          Build_Log.Identify.content(logger.start_date, Some(get_rev()), Some(get_afp_rev())))

        Isabelle_System.bash(
          """rsync -a --include="*/" --include="plain_identify*" --exclude="*" """ +
            Bash.string(backup + "/log/.") + " " + File.bash_path(main_dir) + "/log/.").check

        if (!Isabelle_Devel.cronjob_log.is_file)
          Files.createSymbolicLink(Isabelle_Devel.cronjob_log.file.toPath, current_log.file.toPath)
      })

  val exit =
    Logger_Task("exit", logger =>
      {
        Isabelle_System.bash(
          "rsync -a " + File.bash_path(main_dir) + "/log/." + " " + Bash.string(backup) + "/log/.")
            .check
      })


  /* build release */

  val build_release =
    Logger_Task("build_release", logger =>
      {
        Isabelle_Devel.release_snapshot(logger.options, rev = get_rev(), afp_rev = get_afp_rev())
      })


  /* integrity test of build_history vs. build_history_base */

  val build_history_base =
    Logger_Task("build_history_base", logger =>
      {
        using(logger.ssh_context.open_session("lxbroy10"))(ssh =>
          {
            val results =
              Build_History.remote_build_history(ssh,
                isabelle_repos,
                isabelle_repos.ext("build_history_base"),
                isabelle_identifier = "cronjob_build_history",
                self_update = true,
                rev = "build_history_base",
                options = "-f",
                args = "HOL")

            for ((log_name, bytes) <- results) {
              Bytes.write(logger.log_dir + Path.explode(log_name), bytes)
            }
          })
      })


  /* remote build_history */

  sealed case class Item(
    known: Boolean,
    isabelle_version: String,
    afp_version: Option[String],
    pull_date: Date)
  {
    def unknown: Boolean = !known
    def versions: (String, Option[String]) = (isabelle_version, afp_version)

    def known_versions(rev: String, afp_rev: Option[String]): Boolean =
      known && rev != "" && isabelle_version == rev &&
      (afp_rev.isEmpty || afp_rev.get != "" && afp_version == afp_rev.get)
  }

  def recent_items(db: SQL.Database,
    days: Int, rev: String, afp_rev: Option[String], sql: SQL.Source): List[Item] =
  {
    val afp = afp_rev.isDefined
    val select =
      Build_Log.Data.select_recent_versions(
        days = days, rev = rev, afp_rev = afp_rev, sql = "WHERE " + sql)

    db.using_statement(select)(stmt =>
      stmt.execute_query().iterator(res =>
      {
        val known = res.bool(Build_Log.Data.known)
        val isabelle_version = res.string(Build_Log.Prop.isabelle_version)
        val afp_version = if (afp) proper_string(res.string(Build_Log.Prop.afp_version)) else None
        val pull_date = res.date(Build_Log.Data.pull_date(afp))
        Item(known, isabelle_version, afp_version, pull_date)
      }).toList)
  }

  def unknown_runs(items: List[Item]): List[List[Item]] =
  {
    val (run, rest) = Library.take_prefix[Item](_.unknown, items.dropWhile(_.known))
    if (run.nonEmpty) run :: unknown_runs(rest) else Nil
  }

  sealed case class Remote_Build(
    description: String,
    host: String,
    user: String = "",
    port: Int = 0,
    ssh_host: String = "",
    ssh_permissive: Boolean = false,
    proxy_host: String = "",
    proxy_user: String = "",
    proxy_port: Int = 0,
    self_update: Boolean = false,
    historic: Boolean = false,
    history: Int = 0,
    history_base: String = "build_history_base",
    options: String = "",
    args: String = "",
    afp: Boolean = false,
    bulky: Boolean = false,
    more_hosts: List[String] = Nil,
    detect: SQL.Source = "",
    active: Boolean = true)
  {
    def ssh_session(context: SSH.Context): SSH.Session =
      context.open_session(host = proper_string(ssh_host) getOrElse host, user = user, port = port,
        proxy_host = proxy_host, proxy_user = proxy_user, proxy_port = proxy_port,
        permissive = ssh_permissive)

    def sql: SQL.Source =
      Build_Log.Prop.build_engine + " = " + SQL.string(Build_History.engine) + " AND " +
      SQL.member(Build_Log.Prop.build_host.ident, host :: more_hosts) +
      (if (detect == "") "" else " AND " + SQL.enclose(detect))

    def profile: Build_Status.Profile =
      Build_Status.Profile(description, history = history, afp = afp, bulky = bulky, sql = sql)

    def pick(
      options: Options,
      rev: String = "",
      filter: Item => Boolean = _ => true): Option[(String, Option[String])] =
    {
      val afp_rev = if (afp) Some(get_afp_rev()) else None

      val store = Build_Log.store(options)
      using(store.open_database())(db =>
      {
        def pick_days(days: Int, gap: Int): Option[(String, Option[String])] =
        {
          val items = recent_items(db, days, rev, afp_rev, sql).filter(filter)
          def runs = unknown_runs(items).filter(run => run.length >= gap)

          if (historic || items.exists(_.known_versions(rev, afp_rev))) {
            val longest_run =
              (List.empty[Item] /: runs)({ case (item1, item2) =>
                if (item1.length >= item2.length) item1 else item2
              })
            if (longest_run.isEmpty) None
            else Some(longest_run(longest_run.length / 2).versions)
          }
          else if (rev != "") Some((rev, afp_rev))
          else runs.flatten.headOption.map(_.versions)
        }

        pick_days(options.int("build_log_history") max history, 2) orElse
        pick_days(200, 5) orElse
        pick_days(2000, 1)
      })
    }
  }

  val remote_builds_old: List[Remote_Build] =
    List(
      Remote_Build("AFP", "lxbroy7",
          args = "-N -X large -X slow",
          afp = true,
          detect = Build_Log.Prop.build_tags + " = " + SQL.string("AFP")),
      Remote_Build("Poly/ML 5.7 Linux", "lxbroy8",
        history_base = "37074e22e8be",
        options = "-m32 -B -M1x2,2 -t polyml-5.7 -i 'init_component /home/isabelle/contrib/polyml-5.7'",
        args = "-N -g timing",
        detect = Build_Log.Prop.build_tags + " = " + SQL.string("polyml-5.7") + " AND " +
          Build_Log.Settings.ML_OPTIONS + " <> " + SQL.string("-H 500")),
      Remote_Build("Poly/ML 5.7.1 Linux", "lxbroy8",
        history_base = "a9d5b59c3e12",
        options = "-m32 -B -M1x2,2 -t polyml-5.7.1-pre2 -i 'init_component /home/isabelle/contrib/polyml-test-905dae2ebfda'",
        args = "-N -g timing",
        detect =
          Build_Log.Prop.build_tags + " = " + SQL.string("polyml-5.7.1-pre1") + " OR " +
          Build_Log.Prop.build_tags + " = " + SQL.string("polyml-5.7.1-pre2")),
      Remote_Build("Poly/ML 5.7 Mac OS X", "macbroy2",
        history_base = "37074e22e8be",
        options = "-m32 -B -M1x4,4 -t polyml-5.7 -i 'init_component /home/isabelle/contrib/polyml-5.7'",
        args = "-a",
        detect = Build_Log.Prop.build_tags + " = " + SQL.string("polyml-5.7")),
      Remote_Build("Poly/ML 5.7.1 Mac OS X", "macbroy2",
        history_base = "a9d5b59c3e12",
        options = "-m32 -B -M1x4,4 -t polyml-5.7.1-pre2 -i 'init_component /home/isabelle/contrib/polyml-test-905dae2ebfda'",
        args = "-a",
        detect =
        Build_Log.Prop.build_tags + " = " + SQL.string("polyml-5.7.1-pre1") + " OR " +
        Build_Log.Prop.build_tags + " = " + SQL.string("polyml-5.7.1-pre2")),
      Remote_Build("Poly/ML test", "lxbroy8",
        options = "-m32 -B -M1x2,2 -t polyml-test -i 'init_component /home/isabelle/contrib/polyml-5.7-20170217'",
        args = "-N -g timing",
        detect = Build_Log.Prop.build_tags + " = " + SQL.string("polyml-test")),
      Remote_Build("Mac OS X 10.8 Mountain Lion", "macbroy30", options = "-m32 -M2", args = "-a",
        detect = Build_Log.Prop.build_start + " < date '2017-03-03'"))

  val remote_builds1: List[List[Remote_Build]] =
  {
    List(
      List(Remote_Build("Linux A", "lxbroy9",
        options = "-m32 -B -M1x2,2", args = "-N -g timing")),
      List(Remote_Build("Linux B", "lxbroy10", historic = true, history = 90,
        options = "-m32 -B -M1x4,2,4,6", args = "-N -g timing")),
      List(Remote_Build("Linux Benchmarks", "lxbroy5", historic = true, history = 90,
        options = "-m32 -B -M1x2,2 -t Benchmarks" +
            " -e ISABELLE_GHC=ghc -e ISABELLE_MLTON=mlton -e ISABELLE_OCAML=ocaml" +
            " -e ISABELLE_OCAMLC=ocamlc -e ISABELLE_OCAMLFIND=ocamlfind -e ISABELLE_SMLNJ=sml" +
            " -e ISABELLE_SWIPL=swipl",
          args = "-N -a -d '~~/src/Benchmarks'",
          detect = Build_Log.Prop.build_tags + " = " + SQL.string("Benchmarks"))),
      List(
        Remote_Build("Mac OS X", "macbroy2",
          options = "-m32 -M8" +
            " -e ISABELLE_GHC=ghc -e ISABELLE_MLTON=mlton -e ISABELLE_OCAML=ocaml" +
            " -e ISABELLE_OCAMLC=ocamlc -e ISABELLE_OCAML_SETUP=true" +
            " -e ISABELLE_OPAM_ROOT=\"$ISABELLE_HOME/opam\"" +
            " -e ISABELLE_SMLNJ=/home/isabelle/smlnj/110.85/bin/sml",
          args = "-a",
          detect = Build_Log.Prop.build_tags.undefined,
          history_base = "2c0f24e927dd"),
        Remote_Build("Mac OS X, quick_and_dirty", "macbroy2",
          options = "-m32 -M8 -t quick_and_dirty", args = "-a -o quick_and_dirty",
          detect = Build_Log.Prop.build_tags + " = " + SQL.string("quick_and_dirty"),
          history_base = "2c0f24e927dd"),
        Remote_Build("Mac OS X, skip_proofs", "macbroy2",
          options = "-m32 -M8 -t skip_proofs", args = "-a -o skip_proofs",
          detect = Build_Log.Prop.build_tags + " = " + SQL.string("skip_proofs"),
          history_base = "2c0f24e927dd")),
      List(
        Remote_Build("Mac OS X 10.12 Sierra", "macbroy30", options = "-m32 -M2", args = "-a",
          detect = Build_Log.Prop.build_start + " > date '2017-03-03'")),
      List(Remote_Build("Mac OS X 10.10 Yosemite", "macbroy31", options = "-m32 -M2", args = "-a")),
      List(Remote_Build("Mac OS X 10.14 High Sierra", "lapbroy68", self_update = true,
        options = "-m32 -M1,2,4 -e ISABELLE_GHC_SETUP=true",
        args = "-a -d '~~/src/Benchmarks'")),
      List(Remote_Build("Mac OS X 10.14 Mojave", "lapnipkow3", self_update = true,
        options = "-m32 -M1,2 -e ISABELLE_GHC_SETUP=true",
        args = "-a -d '~~/src/Benchmarks'")),
      List(
        Remote_Build("Windows", "vmnipkow9", historic = true, history = 90, self_update = true,
          options = "-m32 -M4" +
            " -e ISABELLE_OCAML=ocaml -e ISABELLE_OCAMLC=ocamlc -e ISABELLE_OCAML_SETUP=true" +
            " -e ISABELLE_GHC_SETUP=true" +
            " -e ISABELLE_SMLNJ=/usr/local/smlnj-110.81/bin/sml",
          args = "-a",
          detect = Build_Log.Settings.ML_PLATFORM + " = " + SQL.string("x86-windows")),
        Remote_Build("Windows", "vmnipkow9", historic = true, history = 90, self_update = true,
          options = "-m64 -M4" +
            " -e ISABELLE_OCAML=ocaml -e ISABELLE_OCAMLC=ocamlc -e ISABELLE_OCAML_SETUP=true" +
            " -e ISABELLE_GHC_SETUP=true" +
            " -e ISABELLE_SMLNJ=/usr/local/smlnj-110.81/bin/sml",
          args = "-a",
          detect = Build_Log.Settings.ML_PLATFORM + " = " + SQL.string("x86_64-windows")))
    ) :::
    {
      for { (n, hosts) <- List(1 -> List("lxbroy6"), 2 -> List("lxbroy8", "lxbroy5")) }
      yield {
        List(Remote_Build("AFP", host = hosts.head, more_hosts = hosts.tail,
          options = "-m32 -M1x2 -t AFP -P" + n +
            " -e ISABELLE_GHC=ghc" +
            " -e ISABELLE_MLTON=mlton" +
            " -e ISABELLE_OCAML=ocaml -e ISABELLE_OCAMLC=ocamlc -e ISABELLE_OCAMLFIND=ocamlfind" +
            " -e ISABELLE_SMLNJ=sml",
          args = "-N -X large -X slow",
          afp = true,
          detect = Build_Log.Prop.build_tags + " = " + SQL.string("AFP")))
      }
    }
  }

  val remote_builds2: List[List[Remote_Build]] =
    List(
      List(
        Remote_Build("AFP bulky", "lrzcloud1", self_update = true,
          proxy_host = "lxbroy10", proxy_user = "i21isatest",
          ssh_host = "10.155.208.96", ssh_permissive = true,
          options = "-m64 -M6 -U30000 -s10 -t AFP",
          args = "-g large -g slow",
          afp = true,
          bulky = true,
          detect = Build_Log.Prop.build_tags + " = " + SQL.string("AFP"))))

  def remote_build_history(rev: String, afp_rev: Option[String], i: Int, r: Remote_Build)
    : Logger_Task =
  {
    val task_name = "build_history-" + r.host
    Logger_Task(task_name, logger =>
      {
        using(r.ssh_session(logger.ssh_context))(ssh =>
          {
            val results =
              Build_History.remote_build_history(ssh,
                isabelle_repos,
                isabelle_repos.ext(r.host),
                isabelle_identifier = "cronjob_build_history",
                self_update = r.self_update,
                rev = rev,
                afp_rev = afp_rev,
                options =
                  " -N " + Bash.string(task_name) + (if (i < 0) "" else "_" + (i + 1).toString) +
                  " -f -h " + Bash.string(r.host) + " " + r.options,
                args = "-o timeout=10800 " + r.args)

            for ((log_name, bytes) <- results) {
              logger.log(Date.now(), log_name)
              Bytes.write(logger.log_dir + Path.explode(log_name), bytes)
            }
          })
      })
  }

  val build_status_profiles: List[Build_Status.Profile] =
    (remote_builds_old :: remote_builds1 ::: remote_builds2).flatten.map(_.profile)



  /** task logging **/

  object Log_Service
  {
    def apply(options: Options, progress: Progress = No_Progress): Log_Service =
      new Log_Service(SSH.init_context(options), progress)
  }

  class Log_Service private(val ssh_context: SSH.Context, progress: Progress)
  {
    current_log.file.delete

    private val thread: Consumer_Thread[String] =
      Consumer_Thread.fork("cronjob: logger", daemon = true)(
        consume = (text: String) =>
          { // critical
            File.append(current_log, text + "\n")
            File.append(cumulative_log, text + "\n")
            progress.echo(text)
            true
          })

    def shutdown() { thread.shutdown() }

    val hostname = Isabelle_System.hostname()

    def log(date: Date, task_name: String, msg: String): Unit =
      if (task_name != "")
        thread.send(
          "[" + Build_Log.print_date(date) + ", " + hostname + ", " + task_name + "]: " + msg)

    def start_logger(start_date: Date, task_name: String): Logger =
      new Logger(this, start_date, task_name)

    def run_task(start_date: Date, task: Logger_Task)
    {
      val logger = start_logger(start_date, task.name)
      val res = Exn.capture { task.body(logger) }
      val end_date = Date.now()
      val err =
        res match {
          case Exn.Res(_) => None
          case Exn.Exn(exn) =>
            Output.writeln("Exception trace for " + quote(task.name) + ":")
            exn.printStackTrace()
            val first_line = split_lines(Exn.message(exn)).headOption getOrElse "exception"
            Some(first_line)
        }
      logger.log_end(end_date, err)
    }

    def fork_task(start_date: Date, task: Logger_Task): Task =
      new Task(task.name, run_task(start_date, task))
  }

  class Logger private[Isabelle_Cronjob](
    val log_service: Log_Service, val start_date: Date, val task_name: String)
  {
    def ssh_context: SSH.Context = log_service.ssh_context
    def options: Options = ssh_context.options

    def log(date: Date, msg: String): Unit = log_service.log(date, task_name, msg)

    def log_end(end_date: Date, err: Option[String])
    {
      val elapsed_time = end_date.time - start_date.time
      val msg =
        (if (err.isEmpty) "finished" else "ERROR " + err.get) +
        (if (elapsed_time.seconds < 3.0) "" else " (" + elapsed_time.message_hms + " elapsed time)")
      log(end_date, msg)
    }

    val log_dir: Path = main_dir + Build_Log.log_subdir(start_date)

    Isabelle_System.mkdirs(log_dir)
    log(start_date, "started")
  }

  class Task private[Isabelle_Cronjob](name: String, body: => Unit)
  {
    private val future: Future[Unit] = Future.thread("cronjob: " + name) { body }
    def is_finished: Boolean = future.is_finished
  }



  /** cronjob **/

  def cronjob(progress: Progress, exclude_task: Set[String])
  {
    /* soft lock */

    val still_running =
      try { Some(File.read(main_state_file)) }
      catch { case ERROR(_) => None }

    still_running match {
      case None | Some("") =>
      case Some(running) =>
        error("Isabelle cronjob appears to be still running: " + running)
    }


    /* log service */

    val log_service = Log_Service(Options.init(), progress = progress)

    def run(start_date: Date, task: Logger_Task) { log_service.run_task(start_date, task) }

    def run_now(task: Logger_Task) { run(Date.now(), task) }


    /* structured tasks */

    def SEQ(tasks: List[Logger_Task]): Logger_Task = Logger_Task(body = _ =>
      for (task <- tasks.iterator if !exclude_task(task.name) || task.name == "")
        run_now(task))

    def PAR(tasks: List[Logger_Task]): Logger_Task = Logger_Task(body = _ =>
      {
        @tailrec def join(running: List[Task])
        {
          running.partition(_.is_finished) match {
            case (Nil, Nil) =>
            case (Nil, _ :: _) => Thread.sleep(500); join(running)
            case (_ :: _, remaining) => join(remaining)
          }
        }
        val start_date = Date.now()
        val running =
          for (task <- tasks if !exclude_task(task.name))
            yield log_service.fork_task(start_date, task)
        join(running)
      })


    /* repository structure */

    val hg = Mercurial.repository(isabelle_repos)
    val hg_graph = hg.graph()

    def history_base_filter(r: Remote_Build): Item => Boolean =
    {
      val base_rev = hg.id(r.history_base)
      val nodes = hg_graph.all_succs(List(base_rev)).toSet
      (item: Item) => nodes(item.isabelle_version)
    }


    /* main */

    val main_start_date = Date.now()
    File.write(main_state_file, main_start_date + " " + log_service.hostname)

    run(main_start_date,
      Logger_Task("isabelle_cronjob", logger =>
        run_now(
          SEQ(List(
            init,
            build_history_base,
            build_release,
            PAR(
              List(remote_builds1, remote_builds2).map(remote_builds =>
              SEQ(List(
                PAR(remote_builds.map(_.filter(_.active)).map(seq =>
                  SEQ(
                    for {
                      (r, i) <- (if (seq.length <= 1) seq.map((_, -1)) else seq.zipWithIndex)
                      (rev, afp_rev) <- r.pick(logger.options, hg.id(), history_base_filter(r))
                    } yield remote_build_history(rev, afp_rev, i, r)))),
                Logger_Task("jenkins_logs", _ =>
                  Jenkins.download_logs(logger.options, Jenkins.build_log_jobs, main_dir)),
                Logger_Task("build_log_database",
                  logger => Isabelle_Devel.build_log_database(logger.options, build_log_dirs)),
                Logger_Task("build_status",
                  logger => Isabelle_Devel.build_status(logger.options)))))),
            exit)))))

    log_service.shutdown()

    main_state_file.file.delete
  }



  /** command line entry point **/

  def main(args: Array[String])
  {
    Command_Line.tool0 {
      var force = false
      var verbose = false
      var exclude_task = Set.empty[String]

      val getopts = Getopts("""
Usage: Admin/cronjob/main [OPTIONS]

  Options are:
    -f           apply force to do anything
    -v           verbose
    -x NAME      exclude tasks with this name
""",
        "f" -> (_ => force = true),
        "v" -> (_ => verbose = true),
        "x:" -> (arg => exclude_task += arg))

      val more_args = getopts(args)
      if (more_args.nonEmpty) getopts.usage()

      val progress = if (verbose) new Console_Progress() else No_Progress

      if (force) cronjob(progress, exclude_task)
      else error("Need to apply force to do anything")
    }
  }
}
