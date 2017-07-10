theory Test_Pure_Main
imports Protocol_Main
begin

text \<open>\<open>https://github.com/larsrh/libisabelle/issues/61\<close>\<close>

definition test where "test (x::int) = x"
declare test_def[simp]

lemma test2: "a=1 \<longrightarrow> test a=1"
by simp

end
