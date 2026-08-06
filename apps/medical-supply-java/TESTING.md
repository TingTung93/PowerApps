# Testing

`.\build.ps1` compiles with `javac --release 8` and runs every `*Test` class. A green build prints
`<Name>Test: PASS` for each and `Built: ...`.

Headless self-test (no browser): `java -jar dist\MedicalSupply-RC.jar --self-test` prints
`MedicalSupply self-test: PASS`.

Browser smoke: launch the JAR, set a synchronized folder, scan a GS1 barcode, confirm the item
appears in Inventory with the correct expiry color, export a management report, and print labels.
Record results in `qualification/browser-smoke-evidence.md`.
