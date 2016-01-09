theory Test
imports Protocol
begin

operation_setup sleepy = \<open>
  {from_lib = Codec.int,
   to_lib = Codec.unit,
   action = OS.Process.sleep o seconds o real}
\<close>

end
