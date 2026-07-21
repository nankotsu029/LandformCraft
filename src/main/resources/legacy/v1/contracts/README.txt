Immutable legacy v1 contract resources (ADR 0035 D2b, R7).

These files are byte-identical copies of the v1 schemas that still live in the active schemas/
inventory. They exist so the migration-only readers introduced by V2-12-04 can strict-read existing
user assets from the packaged JAR alone, after V2-12-06 removes the v1 schemas from the active
inventory.

Do not edit. LegacyV1ContractResourceTest asserts byte identity with schemas/<name> while both
copies exist; once V2-12-06 removes the active copy, ADR 0035 R7 forbids changing this one without
an ADR amendment.
