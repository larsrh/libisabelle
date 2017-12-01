from info.hupel.isabelle.api import Configuration, Version
from info.hupel.isabelle.japi import JResources, JSetup, JSystem, Operations

res = JResources.dumpIsabelleResources()
config = Configuration.simple("Protocol")
env = JSetup.makeEnvironment(JSetup.defaultSetup(Version.Stable("2017")), res)
sys = JSystem.create(env, config)
response = sys.invoke(Operations.HELLO, "world")
print response

sys.dispose()
