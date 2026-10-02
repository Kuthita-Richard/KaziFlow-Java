package com.kaziflow.custom;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CustomReportHtml — turns a list or grouped report into one self-contained, print-styled HTML page
 * (opened in the browser, where the user prints or saves as PDF). Pure string building, so it is testable.
 * EVERY value is HTML-escaped: record text is typed by users and must never be able to inject markup or script.
 */
public final class CustomReportHtml {

    private CustomReportHtml() {}

    public static String render(String businessName, String title, String subtitle,
                                List<String> headers, List<List<String>> rows, List<String> totalsRow,
                                List<Boolean> rightAligned) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
          .append("<title>").append(esc(title)).append("</title><style>")
          .append("body{font-family:Segoe UI,Arial,sans-serif;color:#1e293b;margin:32px;}")
          .append("h1{font-size:22px;margin:0 0 4px;} .sub{color:#64748b;font-size:13px;margin-bottom:18px;}")
          .append("table{border-collapse:collapse;width:100%;font-size:13px;}")
          .append("th{background:#f1f5f9;text-align:left;padding:7px 10px;border-bottom:2px solid #cbd5e1;}")
          .append("td{padding:6px 10px;border-bottom:1px solid #e2e8f0;} td.r,th.r{text-align:right;}")
          .append("tr.total td{font-weight:bold;border-top:2px solid #1e293b;background:#f8fafc;}")
          .append(".foot{margin-top:16px;color:#94a3b8;font-size:11px;}")
          .append("@media print{body{margin:12mm;} thead{display:table-header-group;} tr{page-break-inside:avoid;}}")
          .append("</style></head><body>");
        if (businessName != null && !businessName.isBlank())
            sb.append("<div class=\"sub\" style=\"margin-bottom:2px\">").append(esc(businessName)).append("</div>");
        sb.append("<h1>").append(esc(title)).append("</h1>");
        if (subtitle != null && !subtitle.isBlank()) sb.append("<div class=\"sub\">").append(esc(subtitle)).append("</div>");
        sb.append("<table><thead><tr>");
        for (int i = 0; i < headers.size(); i++) sb.append("<th").append(right(rightAligned, i) ? " class=\"r\"" : "").append(">").append(esc(headers.get(i))).append("</th>");
        sb.append("</tr></thead><tbody>");
        for (List<String> r : rows) {
            sb.append("<tr>");
            for (int i = 0; i < headers.size(); i++) sb.append("<td").append(right(rightAligned, i) ? " class=\"r\"" : "").append(">").append(esc(i < r.size() ? r.get(i) : "")).append("</td>");
            sb.append("</tr>");
        }
        if (totalsRow != null) {
            sb.append("<tr class=\"total\">");
            for (int i = 0; i < headers.size(); i++) sb.append("<td").append(right(rightAligned, i) ? " class=\"r\"" : "").append(">").append(esc(i < totalsRow.size() ? totalsRow.get(i) : "")).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table><div class=\"foot\">Printed ")
          .append(esc(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))))
          .append(" · ").append(rows.size()).append(rows.size() == 1 ? " row" : " rows").append("</div></body></html>");
        return sb.toString();
    }

    private static boolean right(List<Boolean> aligns, int i) { return aligns != null && i < aligns.size() && Boolean.TRUE.equals(aligns.get(i)); }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
