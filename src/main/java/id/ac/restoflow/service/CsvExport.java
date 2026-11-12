package id.ac.restoflow.service;

import java.util.List;
import java.util.stream.Collectors;

public final class CsvExport {
    private CsvExport() {}
    public static String cell(String value) {
        String s=value==null?"":value;
        String trimmed=s.stripLeading();
        if((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0) || s.startsWith("\t") || s.startsWith("\r"))s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
    public static String render(List<List<String>> rows) {
        return "\ufeff"+rows.stream().map(row->row.stream().map(CsvExport::cell).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n"))+"\r\n";
    }
}
