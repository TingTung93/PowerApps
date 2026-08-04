package org.medsupply;

import java.util.ArrayList;
import java.util.List;

public final class GudidResult {
    public boolean found;
    public String gtin = "";
    public String brandName = "";
    public String companyName = "";
    public String deviceDescription = "";
    public String versionModelNumber = "";
    public String catalogNumber = "";
    public List<String> gmdnTerms = new ArrayList<String>();

    public String suggestedName() {
        return brandName.length() > 0 ? brandName : deviceDescription;
    }

    public String suggestedCategory() {
        StringBuilder sb = new StringBuilder();
        for (String term : gmdnTerms) {
            if (term == null || term.length() == 0) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(term);
        }
        return sb.toString();
    }
}
