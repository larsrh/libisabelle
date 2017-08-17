theory Basic
imports "../protocol/Protocol"
begin

operation_setup (auto) ping = \<open>fn () => ()\<close>

operation_setup (auto) hello = \<open>fn data => "Hello " ^ data\<close>

ML_cond ("2016") \<open>
  val use_thys = Thy_Info.use_thys o map (rpair Position.none)
\<close>

ML_cond ("2016-1") \<open>
  val use_thys = Thy_Info.use_thys o map (rpair Position.none)
\<close>

ML_cond ("2017-RC0") \<open>
  val use_thys = List.app Thy_Info.use_thy
\<close>

operation_setup (sequential, bracket, auto) use_thys = \<open>use_thys\<close>

end
