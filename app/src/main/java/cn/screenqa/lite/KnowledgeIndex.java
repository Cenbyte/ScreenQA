package cn.screenqa.lite;

import android.database.Cursor;
import android.database.sqlite.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Persisted native SQLite full-text index. JSONL is read only during installation. */
final class KnowledgeIndex {
    static final String[] FIELDS={"stage","subject","type","question","options","explanation","knowledge","content"};
    static final double[] WEIGHTS={.3,.6,.2,6,3,1.5,2,2};
    static final class Stats {
        final long records,indexed,relations,empty;final String engine;
        Stats(long records,long indexed,long relations,long empty,String engine){this.records=records;this.indexed=indexed;this.relations=relations;this.empty=empty;this.engine=engine;}
    }
    static Stats build(File file,List<KnowledgeArchive.Dataset> datasets,KnowledgeArchive.Progress progress) throws Exception {
        long total=0;for(KnowledgeArchive.Dataset dataset:datasets)total+=dataset.count;
        long records=0,indexed=0,relations=0,empty=0;
        progress.stage(KnowledgeTask.Step.IMPORT,0,total);
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null)) {
            try(Cursor mode=db.rawQuery("PRAGMA journal_mode=DELETE",null)){mode.moveToFirst();}db.execSQL("PRAGMA synchronous=NORMAL");
            String engine;
            try {db.execSQL("CREATE VIRTUAL TABLE probe USING fts5(value)");db.execSQL("DROP TABLE probe");engine="FTS5";}
            catch(SQLiteException unavailable){engine="FTS4";}
            db.execSQL("CREATE TABLE identities(rid TEXT PRIMARY KEY,source TEXT NOT NULL,type TEXT NOT NULL)");
            db.execSQL("CREATE TABLE docs(rowid INTEGER PRIMARY KEY,rid TEXT UNIQUE,source TEXT,stage TEXT,subject TEXT,type TEXT,question TEXT,options TEXT,answer TEXT,explanation TEXT,knowledge TEXT,content TEXT,provenance TEXT,qkey TEXT)");
            db.execSQL("CREATE INDEX exact_question ON docs(qkey)");db.execSQL("CREATE INDEX metadata ON docs(subject,stage)");
            db.execSQL("CREATE VIRTUAL TABLE search USING "+engine.toLowerCase(Locale.ROOT)+"("+String.join(",",FIELDS)+",tokenize="+(engine.equals("FTS5")?"'unicode61'":"unicode61")+")");
            try(SQLiteStatement identity=db.compileStatement("INSERT INTO identities VALUES(?,?,?)");
                SQLiteStatement record=db.compileStatement("INSERT INTO docs VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                SQLiteStatement text=db.compileStatement("INSERT INTO search(rowid,"+String.join(",",FIELDS)+") VALUES(?,?,?,?,?,?,?,?,?)")) {
                db.beginTransaction();
                try {
                    for(KnowledgeArchive.Dataset dataset:datasets) {
                        MessageDigest digest=MessageDigest.getInstance("SHA-256");long lines=0;
                        try(KnowledgeArchive.Lines reader=new KnowledgeArchive.Lines(new DigestInputStream(new FileInputStream(dataset.file),digest))) {
                            String line;
                            while((line=reader.next())!=null) {
                                lines++;
                                JSONObject object;
                                try {object=new JSONObject(line);validateRecord(object,dataset.name);}
                                catch(Exception invalid){throw new IOException(dataset.name+" 第 "+lines+" 行无效："+invalid.getMessage(),invalid);}
                                String id=object.getString("id"),source=object.getString("source"),type=object.getString("type");
                                identity.bindString(1,id);identity.bindString(2,source);identity.bindString(3,type);identity.executeInsert();
                                records++;
                                if(type.equals("relation"))relations++;
                                else {
                                    String stage=KnowledgeText.scalar(object.opt("stage")),subject=KnowledgeText.scalar(object.opt("subject"));
                                    String question=KnowledgeText.scalar(object.opt("question"));
                                    String options=KnowledgeText.optionText(KnowledgeText.options(object.opt("options")));
                                    String answer=KnowledgeText.scalar(object.opt("answer")),explanation=KnowledgeText.scalar(object.opt("explanation"));
                                    String knowledge=KnowledgeText.material(object.opt("knowledge")),content=KnowledgeText.material(object.opt("content"));
                                    if(question.trim().isEmpty() && knowledge.trim().isEmpty() && content.trim().isEmpty())empty++;
                                    else {
                                    long row=++indexed;record.bindLong(1,row);
                                    String[] values={id,source,stage,subject,type,question,options,answer,explanation,knowledge,content,
                                            KnowledgeText.limit(object.opt("provenance")==null?"":object.opt("provenance").toString(),16000),KnowledgeText.normalizeQuestion(question)};
                                    for(int i=0;i<values.length;i++)record.bindString(i+2,KnowledgeText.limit(values[i],16000));record.executeInsert();
                                    text.bindLong(1,row);String[] fields={stage,subject,type,question,options,explanation,knowledge,content};
                                    for(int i=0;i<fields.length;i++)text.bindString(i+2,KnowledgeText.indexed(fields[i]));text.executeInsert();
                                    }
                                }
                                if(records%500==0)progress.stage(KnowledgeTask.Step.IMPORT,records,total);
                                if(records%1000==0) {db.setTransactionSuccessful();db.endTransaction();db.beginTransaction();}
                            }
                        }
                        if(lines!=dataset.count || !KnowledgeArchive.hex(digest.digest()).equalsIgnoreCase(dataset.sha))
                            throw new IOException("数据集条数或 SHA-256 校验失败："+dataset.name);
                    }
                    db.setTransactionSuccessful();
                } finally {db.endTransaction();}
            }
            progress.stage(KnowledgeTask.Step.IMPORT,records,total);
            progress.stage(KnowledgeTask.Step.INDEX,0,-1);
            db.execSQL("INSERT INTO search(search) VALUES('optimize')");
            db.execSQL("CREATE VIRTUAL TABLE vocab USING "+(engine.equals("FTS5")?"fts5vocab(search,'row')":"fts4aux(search)"));
            try(Cursor cursor=db.rawQuery("PRAGMA quick_check",null)){if(!cursor.moveToFirst() || !"ok".equals(cursor.getString(0)))throw new IOException("索引完整性检查失败");}
            return new Stats(records,indexed,relations,empty,engine);
        }
    }
    private static void validateRecord(JSONObject object,String source) throws Exception {
        for(String key:new String[]{"id","source","stage","subject","type","question","options","answer","explanation","knowledge","content"})
            if(!object.has(key))throw new IOException("缺少字段 "+key);
        if(!(object.get("id") instanceof String) || object.getString("id").isEmpty() || object.getString("id").length()>256)
            throw new IOException("记录 ID 无效");
        if(!source.equals(object.getString("source")))throw new IOException("source 与数据集不一致");
        if(!Arrays.asList("multiple_choice","exercise","instruction_qa","graph_node","knowledge_point","relation").contains(object.getString("type")))throw new IOException("未知记录类型");
        for(String key:new String[]{"stage","subject","question","explanation"})
            if(!object.isNull(key) && !(object.get(key) instanceof String))throw new IOException("字段不是字符串："+key);
        if(!object.isNull("options") && !(object.get("options") instanceof JSONObject))throw new IOException("选项必须为标签对象");
    }
    static final class Hit {
        final String packageId,id,source,stage,subject,type,question,options,answer,explanation,knowledge,content;
        double score;final boolean exact,highValue;
        Hit(String packageId,Cursor cursor,KnowledgeText.Query query,double bm25) {
            this.packageId=packageId;id=cursor.getString(1);source=cursor.getString(2);stage=cursor.getString(3);subject=cursor.getString(4);type=cursor.getString(5);
            question=cursor.getString(6);options=cursor.getString(7);answer=cursor.getString(8);explanation=cursor.getString(9);knowledge=cursor.getString(10);content=cursor.getString(11);
            double similarity=KnowledgeText.similarity(query.stem,question);
            boolean sameStem=!question.isEmpty() && KnowledgeText.normalizeQuestion(query.stem).equals(KnowledgeText.normalizeQuestion(question));
            exact=sameStem && query.sameOptions(options);
            boolean near=similarity>=.94 && KnowledgeText.critical(query.stem).equals(KnowledgeText.critical(question)) && query.sameOptions(options);
            highValue=(exact || near) && !answer.isEmpty();
            score=(bm25/(1+bm25))*2+similarity*8+(question.isEmpty()?0:.3)+(exact?100:0)+(highValue?20:0);
        }
        String identity(){return packageId+"/"+id;}
        String summary(){return KnowledgeText.limit(question.isEmpty()?(knowledge.isEmpty()?content:knowledge):question,160);}
    }
    private static final class Rank {final long id;final double score;Rank(long id,double score){this.id=id;this.score=score;}}
    static List<Hit> search(File file,String engine,String packageId,KnowledgeText.Query query,int limit,BooleanSupplier cancelled) throws Exception {
        Map<Long,Double> candidates=new LinkedHashMap<>();List<Hit> hits=new ArrayList<>();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
            String filter="";List<String> filters=new ArrayList<>();
            if(!query.stage.isEmpty()){filter+=" AND (docs.stage=? OR docs.stage='')";filters.add(query.stage);}
            if(!query.subject.isEmpty()){filter+=" AND (docs.subject=? OR docs.subject='')";filters.add(query.subject);}
            List<String> exactArgs=new ArrayList<>();exactArgs.add(KnowledgeText.normalizeQuestion(query.stem));exactArgs.addAll(filters);
            if(!exactArgs.get(0).isEmpty())try(Cursor cursor=db.rawQuery("SELECT rowid FROM docs WHERE qkey=?"+filter+" LIMIT 30",exactArgs.toArray(new String[0]))) {
                while(cursor.moveToNext())candidates.put(cursor.getLong(0),10.0);
            }
            LinkedHashSet<String> tokens=new LinkedHashSet<>(KnowledgeText.tokens(query.stem,false));
            tokens.addAll(KnowledgeText.tokens(query.whole,false));
            List<String> all=new ArrayList<>(tokens);if(all.size()>128) {
                List<String> sampled=new ArrayList<>();for(int i=0;i<128;i++)sampled.add(all.get(i*(all.size()-1)/127));all=sampled;
            }
            if(!all.isEmpty()) {
                List<String> available=new ArrayList<>();Map<String,Long> frequencies=new HashMap<>();
                String slots=String.join(",",Collections.nCopies(all.size(),"?"));
                String vocab=engine.equals("FTS5")?"SELECT term,doc FROM vocab WHERE term IN ("+slots+")":"SELECT term,documents FROM vocab WHERE col='*' AND term IN ("+slots+")";
                try(Cursor cursor=db.rawQuery(vocab,all.toArray(new String[0]))) {while(cursor.moveToNext()){available.add(cursor.getString(0));frequencies.put(cursor.getString(0),cursor.getLong(1));}}
                Set<String> stemTokens=new HashSet<>(KnowledgeText.tokens(query.stem,false));
                available.sort(Comparator.<String>comparingInt(t->stemTokens.contains(t)?0:1).thenComparingLong(frequencies::get));
                List<String> terms=new ArrayList<>();int extras=0;
                for(String token:available) {
                    if(!stemTokens.contains(token) && ++extras>3)continue;
                    if(terms.size()==18)break;terms.add("\""+token.replace("\"","\"\"")+"\"");
                }
                if(!terms.isEmpty()) {
                    // Keep FTS as the outer table: the metadata index otherwise makes Android
                    // rescan the entire MATCH result once per matching metadata row.
                    String match=String.join(" OR ",terms);List<String> args=new ArrayList<>();args.add(match);args.addAll(filters);
                    if(engine.equals("FTS5"))try(Cursor cursor=db.rawQuery("SELECT search.rowid,-bm25(search,0.3,0.6,0.2,6,3,1.5,2,2) FROM search CROSS JOIN docs ON docs.rowid=search.rowid WHERE search MATCH ?"+filter+" ORDER BY bm25(search,0.3,0.6,0.2,6,3,1.5,2,2) LIMIT 80",args.toArray(new String[0]))) {
                        while(cursor.moveToNext()){checkCancelled(cancelled);candidates.put(cursor.getLong(0),cursor.getDouble(1));}
                    } else {
                        PriorityQueue<Rank> best=new PriorityQueue<>(Comparator.comparingDouble(r->r.score));
                        try(Cursor cursor=db.rawQuery("SELECT search.rowid,matchinfo(search,'pcnalx') FROM search CROSS JOIN docs ON docs.rowid=search.rowid WHERE search MATCH ?"+filter,args.toArray(new String[0]))) {
                            while(cursor.moveToNext()) {
                                checkCancelled(cancelled);Rank rank=new Rank(cursor.getLong(0),bm25(cursor.getBlob(1)));
                                if(best.size()<80)best.add(rank);else if(rank.score>best.peek().score){best.poll();best.add(rank);}
                            }
                        }
                        for(Rank rank:best)candidates.putIfAbsent(rank.id,rank.score);
                    }
                }
            }
            // Retrieve display fields only for a bounded set of ranked candidates.
            for(Map.Entry<Long,Double> candidate:candidates.entrySet()) {
                    checkCancelled(cancelled);
                    try(Cursor cursor=db.rawQuery("SELECT * FROM docs WHERE rowid=?",new String[]{String.valueOf(candidate.getKey())})) {
                        if(cursor.moveToFirst())hits.add(new Hit(packageId,cursor,query,candidate.getValue()));
                    }
            }
        }
        hits.sort(Comparator.comparingDouble((Hit hit)->hit.score).reversed().thenComparing(hit->hit.id));
        return new ArrayList<>(hits.subList(0,Math.min(limit,hits.size())));
    }
    static void checkCancelled(BooleanSupplier cancelled)throws InterruptedIOException{if(cancelled.getAsBoolean())throw new InterruptedIOException("本地检索已取消");}
    /** FTS4 pc-n-a-l-x: BM25F combines field-weighted TF with per-field length normalization. */
    static double bm25(byte[] info) {
        IntBuffer data=ByteBuffer.wrap(info).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int phrases=data.get(0),columns=data.get(1),documents=data.get(2);double score=0;
        int averages=3,lengths=3+columns,matches=3+2*columns;
        for(int phrase=0;phrase<phrases;phrase++) {
            double tf=0;long df=0;
            for(int column=0;column<columns;column++) {
                int offset=matches+(phrase*columns+column)*3;
                long hits=Integer.toUnsignedLong(data.get(offset));df=Math.max(df,Integer.toUnsignedLong(data.get(offset+2)));
                double average=Math.max(1,Integer.toUnsignedLong(data.get(averages+column)));
                double length=Integer.toUnsignedLong(data.get(lengths+column));
                tf+=WEIGHTS[column]*hits/(.25+.75*length/average);
            }
            if(tf>0)score+=Math.log(1+(documents-df+.5)/(df+.5))*tf*2.2/(tf+1.2);
        }
        return score;
    }
    private KnowledgeIndex() { }
}
