/*  Title:      Pure/Admin/isabelle_devel.scala
    Author:     Makarius

Website for Isabelle development resources.
*/

package isabelle


object Isabelle_Devel
{
  val root = Path.explode("~/html-data/devel")

  val RELEASE_SNAPSHOT = "release_snapshot"
  val BUILD_LOG_DB = "build_log.db"
  val BUILD_STATUS = "build_status"

  val standard_log_dirs =
    List(Path.explode("~/log"), Path.explode("~/afp/log"), Path.explode("~/cronjob/log"))


  /* index */

  def make_index()
  {
    val header = "Isabelle Development Resources"

    HTML.write_document(root, "index.html",
      List(HTML.title(header)),
      List(HTML.chapter(header),
        HTML.itemize(
          List(
            HTML.text("Isabelle nightly ") :::
            List(HTML.link(RELEASE_SNAPSHOT, HTML.text("release snapshot"))) :::
            HTML.text(" (for all platforms)"),

            HTML.text("Isabelle ") :::
            List(HTML.link(BUILD_STATUS + "/index.html", HTML.text("build status"))) :::
            HTML.text(" information"),

            HTML.text("Database with recent ") :::
            List(HTML.link(BUILD_LOG_DB, HTML.text("build log"))) :::
            HTML.text(" information (e.g. for ") :::
            List(HTML.link("http://sqlitebrowser.org",
              List(HTML.code(HTML.text("sqlitebrowser"))))) :::
            HTML.text(")")))))
  }


  /* release snapshot */

  def release_snapshot(
    rev: String = "",
    afp_rev: String = "",
    parallel_jobs: Int = 1,
    remote_mac: String = "")
  {
    Isabelle_System.with_tmp_dir("isadist")(base_dir =>
      {
        Isabelle_System.update_directory(root + Path.explode(RELEASE_SNAPSHOT),
          website_dir =>
            Build_Release.build_release(base_dir, rev = rev, afp_rev = afp_rev,
              parallel_jobs = parallel_jobs, remote_mac = remote_mac, website = Some(website_dir)))
      })
  }


  /* maintain build_log database */

  def build_log_database(options: Options, log_dirs: List[Path] = standard_log_dirs)
  {
    val store = Build_Log.store(options)
    using(store.open_database())(db =>
    {
      store.update_database(db, log_dirs)
      store.update_database(db, log_dirs, ml_statistics = true)
      store.snapshot_database(db, root + Path.explode(BUILD_LOG_DB))
    })
  }


  /* present build status */

  def build_status(options: Options)
  {
    Isabelle_System.update_directory(root + Path.explode(BUILD_STATUS),
      dir => Build_Status.build_status(options, target_dir = dir, ml_statistics = true))
  }
}
