import sys

file_path_rs = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\RapidScan.pa.yaml'
with open(file_path_rs, 'r', encoding='utf-8') as f:
    content = f.read()

old_no_parens = '''                                            // No parentheses fallback
                                            {
                                                gtin_out: If(StartsWith(cleanPrefix, "01"), Mid(cleanPrefix, 3, 14), ""),
                                                expiry_out: With({eIdx: Find("17", cleanPrefix, 15)}, If(eIdx > 0, Mid(cleanPrefix, eIdx + 2, 6), "")),
                                                batch_out: With({bIdx: Find("10", cleanPrefix, 15)}, If(bIdx > 0, 
                                                    With({
                                                        rem: Mid(cleanPrefix, bIdx + 2), 
                                                        gsIdx: Find(Char(29), Mid(cleanPrefix, bIdx + 2))
                                                    },
                                                    If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), "")),
                                                serial_out: With({sIdx: Find("21", cleanPrefix, 15)}, If(sIdx > 0, 
                                                    With({
                                                        rem: Mid(cleanPrefix, sIdx + 2), 
                                                        gsIdx: Find(Char(29), Mid(cleanPrefix, sIdx + 2))
                                                    },
                                                    If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), "")),
                                                serial91_out: With({sIdx: Find("91", cleanPrefix, 15)}, If(sIdx > 0, 
                                                    With({
                                                        rem: Mid(cleanPrefix, sIdx + 2), 
                                                        gsIdx: Find(Char(29), Mid(cleanPrefix, sIdx + 2))
                                                    },
                                                    If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), ""))
                                            }'''

new_no_parens = '''                                            // No parentheses fallback
                                            {
                                                gtin_out: If(StartsWith(cleanPrefix, "01"), Mid(cleanPrefix, 3, 14), ""),
                                                expiry_out: Coalesce(
                                                    If(Find("17", cleanPrefix) = 1, Mid(cleanPrefix, 3, 6), Blank()),
                                                    If(Find("17", cleanPrefix) = 17, Mid(cleanPrefix, 19, 6), Blank()),
                                                    If(Find("17", cleanPrefix) = 25, Mid(cleanPrefix, 27, 6), Blank()),
                                                    With({idx: Find(Char(29) & "17", cleanPrefix)}, If(idx > 0, Mid(cleanPrefix, idx + 3, 6), Blank())),
                                                    ""
                                                ),
                                                batch_out: With({
                                                    bStart: Coalesce(
                                                        If(Find("10", cleanPrefix) = 1, 3, Blank()),
                                                        If(Find("10", cleanPrefix) = 17, 19, Blank()),
                                                        If(Find("10", cleanPrefix) = 25, 27, Blank()),
                                                        With({idx: Find(Char(29) & "10", cleanPrefix)}, If(idx > 0, idx + 3, Blank()))
                                                    )
                                                }, If(!IsBlank(bStart), 
                                                    With({rem: Mid(cleanPrefix, bStart), gsIdx: Find(Char(29), Mid(cleanPrefix, bStart))}, 
                                                        If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), 
                                                "")),
                                                serial_out: With({
                                                    sStart: Coalesce(
                                                        If(Find("21", cleanPrefix) = 1, 3, Blank()),
                                                        If(Find("21", cleanPrefix) = 17, 19, Blank()),
                                                        If(Find("21", cleanPrefix) = 25, 27, Blank()),
                                                        With({idx: Find(Char(29) & "21", cleanPrefix)}, If(idx > 0, idx + 3, Blank()))
                                                    )
                                                }, If(!IsBlank(sStart), 
                                                    With({rem: Mid(cleanPrefix, sStart), gsIdx: Find(Char(29), Mid(cleanPrefix, sStart))}, 
                                                        If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), 
                                                "")),
                                                serial91_out: With({
                                                    sStart: Coalesce(
                                                        If(Find("91", cleanPrefix) = 1, 3, Blank()),
                                                        If(Find("91", cleanPrefix) = 17, 19, Blank()),
                                                        If(Find("91", cleanPrefix) = 25, 27, Blank()),
                                                        With({idx: Find(Char(29) & "91", cleanPrefix)}, If(idx > 0, idx + 3, Blank()))
                                                    )
                                                }, If(!IsBlank(sStart), 
                                                    With({rem: Mid(cleanPrefix, sStart), gsIdx: Find(Char(29), Mid(cleanPrefix, sStart))}, 
                                                        If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), 
                                                ""))
                                            }'''

if old_no_parens in content:
    content = content.replace(old_no_parens, new_no_parens)
    with open(file_path_rs, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Success: RapidScan updated")
else:
    print("Error: Target not found in RapidScan")
