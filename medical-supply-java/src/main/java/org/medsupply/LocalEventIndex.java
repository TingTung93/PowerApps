package org.medsupply;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public final class LocalEventIndex {
    private final Path file; private final Map<String,Entry> entries=new HashMap<String,Entry>();
    public LocalEventIndex(Path localRoot)throws IOException{Files.createDirectories(localRoot.resolve("index"));file=localRoot.resolve("index").resolve("events-index.json");load();}
    public synchronized Entry find(String relativePath,long size,long modifiedMillis){Entry e=entries.get(relativePath);return e!=null&&e.size==size&&e.modifiedMillis==modifiedMillis?e:null;}
    public synchronized void replace(Map<String,Entry> updated)throws IOException{entries.clear();entries.putAll(updated);List<Object> rows=new ArrayList<Object>();for(Entry e:updated.values()){Map<String,Object> row=new LinkedHashMap<String,Object>();row.put("relativePath",e.relativePath);row.put("size",Double.valueOf(e.size));row.put("modifiedMillis",Double.valueOf(e.modifiedMillis));row.put("fileHash",e.fileHash);row.put("json",e.json);rows.add(row);}Path partial=file.resolveSibling(file.getFileName()+".partial");Files.write(partial,Json.write(rows).getBytes(StandardCharsets.UTF_8));Files.move(partial,file,StandardCopyOption.REPLACE_EXISTING);}
    public synchronized void clear()throws IOException{entries.clear();Files.deleteIfExists(file);}
    private void load()throws IOException{if(!Files.isRegularFile(file))return;try{String text=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);for(Object row:Json.asList(Json.parse(text))){Map<String,Object> m=Json.asMap(row);String relative=Json.str(m,"relativePath");entries.put(relative,new Entry(relative,(long)number(m.get("size")),(long)number(m.get("modifiedMillis")),Json.str(m,"fileHash"),Json.str(m,"json")));}}catch(RuntimeException ignored){entries.clear();}}
    private static double number(Object value){return value instanceof Number?((Number)value).doubleValue():0;}
    public static final class Entry { public final String relativePath;public final long size;public final long modifiedMillis;public final String fileHash;public final String json;public Entry(String relativePath,long size,long modifiedMillis,String fileHash,String json){this.relativePath=relativePath;this.size=size;this.modifiedMillis=modifiedMillis;this.fileHash=fileHash;this.json=json;} }
}
