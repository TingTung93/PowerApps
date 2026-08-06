let
    // Required parameters:
    // SiteUrl: SharePoint site URL, not the list URL.
    // TrackingListName: normally "Tracking".
    // ReportUtcOffsetHours: MTF local offset from UTC, for example -8 for PST.

    Source = SharePoint.Tables(
        SiteUrl,
        [
            Implementation = "2.0",
            ViewMode = "All"
        ]
    ),
    MatchingLists = Table.SelectRows(
        Source,
        each
            Record.FieldOrDefault(_, "Title", null) = TrackingListName
            or Record.FieldOrDefault(_, "Name", null) = TrackingListName
    ),
    RawItems =
        if Table.RowCount(MatchingLists) = 1 then
            MatchingLists{0}[Items]
        else if Table.RowCount(MatchingLists) = 0 then
            error "The SharePoint list '" & TrackingListName & "' was not found at " & SiteUrl
        else
            error "More than one SharePoint list matched '" & TrackingListName & "'. Use the list GUID in the query.",
            
    // UPDATED: Using the exact column headers you provided
    SelectedColumns = Table.SelectColumns(
        RawItems,
        {
            "ID",
            "Tracking Number",
            "Carrier",
            "Direction",
            "Status",
            "Recipient",
            "Sender",
            "LoggedDateTime",
            "Notes",
            "SignedOutDateTime",
            "SignedOutBy",
            "Location",
            "ManifestID",
            "ManifestPrintedBy",
            "ReceivedAt",
            "ReceivedBy",
            "Created",
            "Modified",
            "Created By",
            "Modified By"
        },
        MissingField.UseNull
    ),
    
    // UPDATED: Only renaming the date columns to match the local time script below
    RenamedColumns = Table.RenameColumns(
        SelectedColumns,
        {
            {"LoggedDateTime", "Logged At"},
            {"SignedOutDateTime", "Signed Out At"}
        },
        MissingField.Ignore
    ),
    PersonDisplayName = (person as any) as nullable text =>
        if person = null then
            null
        else if Value.Is(person, type record) then
            Text.From(
                Record.FieldOrDefault(
                    person,
                    "DisplayName",
                    Record.FieldOrDefault(
                        person,
                        "displayName",
                        Record.FieldOrDefault(
                            person,
                            "Title",
                            Record.FieldOrDefault(person, "Email", null)
                        )
                    )
                )
            )
        else
            try Text.From(person) otherwise null,
            
    AddReceivedBy = Table.AddColumn(RenamedColumns, "Received By Name", each PersonDisplayName([ReceivedBy]), type text),
    AddSignedOutBy = Table.AddColumn(AddReceivedBy, "Signed Out By Name", each PersonDisplayName([SignedOutBy]), type text),
    AddManifestPrintedBy = Table.AddColumn(AddSignedOutBy, "Manifest Printed By Name", each PersonDisplayName([ManifestPrintedBy]), type text),
    
    // UPDATED: Now referencing "Created By" and "Modified By" instead of Author/Editor
    AddCreatedBy = Table.AddColumn(AddManifestPrintedBy, "Created By Name", each PersonDisplayName([#"Created By"]), type text),
    AddModifiedBy = Table.AddColumn(AddCreatedBy, "Modified By Name", each PersonDisplayName([#"Modified By"]), type text),
    
    RemovePersonRecords = Table.RemoveColumns(
        AddModifiedBy,
        {"ReceivedBy", "SignedOutBy", "ManifestPrintedBy", "Created By", "Modified By"},
        MissingField.Ignore
    ),
    TypedTimestamps = Table.TransformColumnTypes(
        RemovePersonRecords,
        {
            {"ID", Int64.Type},
            {"Logged At", type datetime},
            {"ReceivedAt", type datetime},
            {"Signed Out At", type datetime},
            {"Created", type datetime},
            {"Modified", type datetime}
        }
    ),
    ToLocal = (value as nullable datetime) as nullable datetime =>
        if value = null then
            null
        else
            DateTimeZone.RemoveZone(
                DateTimeZone.SwitchZone(
                    DateTime.AddZone(value, 0),
                    ReportUtcOffsetHours
                )
            ),
    AddLoggedLocal = Table.AddColumn(TypedTimestamps, "Logged At Local", each ToLocal([Logged At]), type datetime),
    AddReceivedLocal = Table.AddColumn(AddLoggedLocal, "Received At Local", each ToLocal([ReceivedAt]), type datetime),
    AddSignedOutLocal = Table.AddColumn(AddReceivedLocal, "Signed Out At Local", each ToLocal([Signed Out At]), type datetime),
    AddCreatedLocal = Table.AddColumn(AddSignedOutLocal, "Created Local", each ToLocal([Created]), type datetime),
    AddModifiedLocal = Table.AddColumn(AddCreatedLocal, "Modified Local", each ToLocal([Modified]), type datetime),
    
    // UPDATED: Formatting text values using the correct column names
    CleanText = Table.TransformColumns(
        AddModifiedLocal,
        {
            {"Tracking Number", each if _ = null then null else Text.Trim(Text.From(_)), type text},
            {"Carrier", each if _ = null then null else Text.Trim(Text.From(_)), type text},
            {"Direction", each if _ = null then null else Text.Trim(Text.From(_)), type text},
            {"Status", each if _ = null then null else Text.Trim(Text.From(_)), type text},
            {"Recipient", each if _ = null then null else Text.Trim(Text.From(_)), type text},
            {"Sender", each if _ = null then null else Text.Trim(Text.From(_)), type text},
            {"Location", each if _ = null then null else Text.Trim(Text.From(_)), type text},
            {"ManifestID", each if _ = null then null else Text.Trim(Text.From(_)), type text}
        },
        null,
        MissingField.Ignore
    ),
    AddPackageReference = Table.AddColumn(
        CleanText,
        "Package Reference",
        each "PKGID-" & Text.From([ID], "en-US"),
        type text
    ),
    FinalColumns = Table.ReorderColumns(
        AddPackageReference,
        {
            "ID",
            "Package Reference",
            "Tracking Number",
            "Carrier",
            "Direction",
            "Status",
            "Recipient",
            "Sender",
            "Location",
            "Received At Local",
            "Received By Name",
            "Signed Out At Local",
            "Signed Out By Name",
            "ManifestID",
            "Manifest Printed By Name",
            "Logged At Local",
            "Created Local",
            "Created By Name",
            "Modified Local",
            "Modified By Name",
            "Notes"
        },
        MissingField.Ignore
    )
in
    FinalColumns
