# Runtime dependency scoped review

No substantive finding in the proposed package fix.

`tensor-plugin-tushare` has a compile dependency on `json-schema-validator`, and production `DatasetDefinitionLoader` directly links `SpecVersion.VersionFlag`. The app's nearer direct declaration of the same artifact with `scope=test` won Maven dependency mediation and excluded the library from the Boot runtime package. Removing only that scope line makes the existing explicit app dependency compile/runtime and is the minimum deterministic fix.

The existing production package-contract method now requires the exact `BOOT-INF/lib/json-schema-validator-1.5.9.jar` and opens that nested JAR to require `com/networknt/schema/SpecVersion$VersionFlag.class`. This directly covers the observed startup failure without adding a test method or changing the release report counts. The acceptance artifact copies the production JAR entries before adding the fixture plugin and V6 migration, so the production assertion covers its shared runtime dependency too.

The root coordinator reported the old-package RED as 4 tests with one failure for the missing validator JAR in `/tmp/issue018-t12-runtime-dependency-red.log`. I did not rerun Maven or package commands during this static review.
