theory Basic
imports "../protocol/Protocol"
begin

operation_setup (auto) ping = \<open>fn () => ()\<close>

operation_setup (auto) hello = \<open>fn data => "Hello " ^ data\<close>

operation_setup (sequential, bracket, auto) use_thys =
  \<open>Thy_Info.use_thys o map (rpair Position.none)\<close>

end
