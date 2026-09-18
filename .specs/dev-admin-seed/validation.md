# Validation
JDK 21, from services/inventory-service:

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -q clean test -Dtest=DevSeedControllerTest,UserServiceTest,ProductionSafeConfigurationTest,ArchitectureTest`

Passed: 25 tests, no failures/errors. New tests first failed against pre-fix behavior:
admin missing on already-seeded path and existing designated admin stayed EMPLOYEE.

Initial sandbox run could not attach Mockito; reran outside sandbox. Initial broader run
found new frozen repository access sites and a missing generated NotificationMapper bean.
The final loop preserves existing call sites and clean build regenerates mappers; all checks
pass without architecture baseline changes.

No endpoint shape changes; generated contracts unchanged. No container rebuild, seed API
invocation, or production changes performed by this task. Rebuild inventory-service before
rerunning seed/all. PR gate remains pending.
