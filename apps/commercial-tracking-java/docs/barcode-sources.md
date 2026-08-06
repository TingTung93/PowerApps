# Barcode Parsing Sources

Research baseline for the release-candidate parser.

## GS1

- GS1 General Specifications, release 26.0: <https://ref.gs1.org/standards/genspecs/>
- GS1 Application Identifier reference: <https://ref.gs1.org/ai/>
- GS1 transport-process implementation guideline: <https://www.gs1.org/standards/encoding-transport-process-information-gs1-implementation-guideline/10>

RC parser support includes AI 00, 01, 10, 17, 21, 30, 37, 330n–336n, 401, 402, 403, 410, 420, 421, and 4300–4306. Support is intentionally bounded and fixture-tested.

## FedEx / ANSI MH10

- FedEx Ship API documentation identifies its PDF417 two-dimensional label data as ANSI MH10.8.3: <https://developer.fedex.com/api/en-cz/catalog/ship/docs.html>

The RC retains compatibility with the existing Power App's observed `31Z` tracking identifier and metadata hints such as `11Z`/`12Z`. Those carrier-specific identifiers require validation against representative labels because the full ANSI standard text is not bundled with the repository.

## USPS

- USPS Intelligent Mail Package Barcode resources and specification links: <https://postalpro.usps.com/shipping/impb>

The RC recognizes common IMpb strings beginning 91–94 and international S10-style identifiers ending in `US`. It does not claim full IMpb field decomposition.

## Carrier heuristics

UPS, FedEx, DHL, and Amazon length/prefix recognition is treated as carrier-specific identification, not proof that a shipment exists. External carrier APIs are outside the release-candidate scan path.

## Fixture policy

Use synthetic structurally valid values wherever possible. If a production label is required to diagnose a format, redact or transform recipient, address, account, and tracking content while preserving delimiters and field lengths. Raw scans must not be committed without approval.
