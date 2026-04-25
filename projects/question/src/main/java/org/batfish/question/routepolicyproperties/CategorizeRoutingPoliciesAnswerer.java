package org.batfish.question.routepolicyproperties;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.BgpPeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.communities.SetCommunities;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;
import org.batfish.datamodel.routing_policy.statement.TraceableStatement;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;


public class CategorizeRoutingPoliciesAnswerer extends Answerer {

  public static final String COL_NODE = "Node";
  public static final String COL_IMPORT_TAG = "Import_Tag";
  public static final String COL_IMPORT_ACCEPT_ALL = "Import_AcceptAll";
  public static final String COL_IMPORT_DENY_ALL = "Import_DenyAll";
  public static final String COL_IMPORT_DENY_PART = "Import_DenyPart";
  public static final String COL_EXPORT_TAG = "Export_Tag";
  public static final String COL_EXPORT_ACCEPT_ALL = "Export_AcceptAll";
  public static final String COL_EXPORT_DENY_ALL = "Export_DenyAll";
  public static final String COL_EXPORT_DENY_PART = "Export_DenyPart";
  public static final String COL_OTHER = "Other";

  public CategorizeRoutingPoliciesAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    CategorizeRoutingPoliciesQuestion question = (CategorizeRoutingPoliciesQuestion) _question;
    TableMetadata tableMetadata = createTableMetadata();
    TableAnswerElement answer = new TableAnswerElement(tableMetadata);

    List<Row> rows =
            getAnswerRows(
                    _batfish.specifierContext(snapshot),
                    question.getNodeSpecifier(),
                    tableMetadata.toColumnMap());

    answer.postProcessAnswer(question, rows);
    return answer;
  }

  public enum Category {
    Tag,
    DenyPart,
    AcceptAll,
    DenyAll,
    Other
  }

  static class Flags{
    boolean setCommunity;
    boolean hasAccept;
    boolean hasReject;
  }

  private static void scan(List<Statement> sts, Flags f) {
    for (Statement s : sts) {
      if (s instanceof SetCommunities) {
        f.setCommunity = true;
      }
      else if (s instanceof Statements.StaticStatement ss) {
        Statements t = ss.getType();
        if (t == Statements.ExitAccept || t == Statements.ReturnTrue)  f.hasAccept = true;
        if (t == Statements.ExitReject || t == Statements.ReturnFalse) f.hasReject = true;
      }
      else if (s instanceof If ifs) {
        scan(ifs.getTrueStatements(), f);
        scan(ifs.getFalseStatements(), f);
      }
      else if (s instanceof TraceableStatement ts) {
        scan(ts.getInnerStatements(), f);
      }
    }
  }

  private static boolean mayExit(List<Statement> sts) {
    for (Statement s : sts) {
      if (s instanceof Statements.StaticStatement ss) {
        Statements t = ss.getType();
        if (t == Statements.ExitAccept || t == Statements.ExitReject
                || t == Statements.ReturnTrue || t == Statements.ReturnFalse) return true;
      }
      if (s instanceof If ifs) {
        if (mayExit(ifs.getTrueStatements()) || mayExit(ifs.getFalseStatements())) return true;
      }
      if (s instanceof TraceableStatement ts) {
        if (mayExit(ts.getInnerStatements())) return true;
      }
    }
    return false;
  }


  private static boolean terminates(List<Statement> sts, boolean ifAccept) {
    Statements Return = (ifAccept) ? Statements.ExitAccept : Statements.ExitReject;
    Statements altReturn = (ifAccept) ? Statements.ReturnTrue : Statements.ReturnFalse;
    Statements rReturn = (!ifAccept) ? Statements.ExitAccept : Statements.ExitReject;
    Statements rAltReturn = (!ifAccept) ? Statements.ReturnTrue : Statements.ReturnFalse;
    for (Statement s : sts) {
      if (s instanceof Statements.StaticStatement ss){
        if (ss.getType() == Return || ss.getType() == altReturn) return true;
        if (ss.getType() == rReturn || ss.getType() == rAltReturn) return false;
      }
      else if (s instanceof If ifs) {
        boolean guardTrue = ifs.getGuard() == BooleanExprs.TRUE;
        if (guardTrue && terminates(ifs.getTrueStatements(), ifAccept)) return true;
        if (terminates(ifs.getTrueStatements(), ifAccept) && terminates(ifs.getFalseStatements(), ifAccept)) return true;
        if (mayExit(ifs.getTrueStatements()) || mayExit(ifs.getFalseStatements()))return false;
      }
      else if (s instanceof TraceableStatement ts) {
        if (terminates(ts.getInnerStatements(), ifAccept)) return true;
        if (mayExit(ts.getInnerStatements())) return false;
      }
    }
    return false;
  }


  private static Category getCategory(RoutingPolicy policy){
    if (policy == null) return Category.Other;
    List<Statement> sts = policy.getStatements();
    Flags f = new Flags();
    scan(sts, f);
    if(f.setCommunity)return Category.Tag;

    if(terminates(sts, true))return Category.AcceptAll;
    if(terminates(sts, false))return Category.DenyAll;

    if(f.hasReject || f.hasAccept)return Category.DenyPart;
    return Category.Other;
  }

  @VisibleForTesting
  static List<Row> getAnswerRows(
          SpecifierContext ctxt,
          NodeSpecifier nodeSpecifier,
          Map<String, ColumnMetadata> columnMap) {
    ImmutableList.Builder<Row> rows = ImmutableList.builder();

    int totImportTag=0, totImportAll=0, totImportNone=0, totImportPart=0;
    int totExportTag=0, totExportAll=0, totExportNone=0, totExportPart=0, totOther=0;

    Map<String, Category> classified = new HashMap<>();

    for(String node : nodeSpecifier.resolve(ctxt)){
      classified.clear();
      Configuration config = ctxt.getConfigs().get(node);
      if (config.getDefaultVrf() == null) continue;
      BgpProcess bProcess = config.getDefaultVrf().getBgpProcess();
      if(bProcess == null)continue;

      int importTag=0, importAll=0, importNone=0, importPart=0;
      int exportTag=0, exportAll=0, exportNone=0, exportPart=0, other=0;

      for(BgpPeerConfig peer : Iterables.concat(
              bProcess.getActiveNeighbors().values(),
              bProcess.getPassiveNeighbors().values())){
        Ipv4UnicastAddressFamily af = peer.getIpv4UnicastAddressFamily();
        if(af==null)continue;
        for(String im : af.getImportPolicySources()){
          RoutingPolicy policy = config.getRoutingPolicies().get(im);
          Category cat = classified.computeIfAbsent(im, k -> getCategory(policy));
          switch(cat){
            case Tag -> importTag++;
            case AcceptAll -> importAll++;
            case DenyAll -> importNone++;
            case DenyPart -> importPart++;
            default -> other++;
          }
        }
        for(String ex : af.getExportPolicySources()){
          RoutingPolicy policy = config.getRoutingPolicies().get(ex);
          Category cat = classified.computeIfAbsent(ex, k -> getCategory(policy));
          switch(cat){
            case Tag -> exportTag++;
            case AcceptAll -> exportAll++;
            case DenyAll -> exportNone++;
            case DenyPart -> exportPart++;
            default -> other++;
          }
        }
      }

      rows.add(Row.builder(columnMap)
              .put(COL_NODE, node)
              .put(COL_IMPORT_TAG, importTag)
              .put(COL_IMPORT_ACCEPT_ALL, importAll)
              .put(COL_IMPORT_DENY_ALL, importNone)
              .put(COL_IMPORT_DENY_PART, importPart)
              .put(COL_EXPORT_TAG, exportTag)
              .put(COL_EXPORT_ACCEPT_ALL, exportAll)
              .put(COL_EXPORT_DENY_ALL, exportNone)
              .put(COL_EXPORT_DENY_PART, exportPart)
              .put(COL_OTHER, other)
              .build());

      totImportTag += importTag;
      totImportAll += importAll;
      totImportNone += importNone;
      totImportPart += importPart;
      totExportTag += exportTag;
      totExportAll += exportAll;
      totExportNone += exportNone;
      totExportPart += exportPart;
      totOther += other;
    }

    rows.add(Row.builder(columnMap)
            .put(COL_NODE, "TOTAL")
            .put(COL_IMPORT_TAG, totImportTag)
            .put(COL_IMPORT_ACCEPT_ALL, totImportAll)
            .put(COL_IMPORT_DENY_ALL, totImportNone)
            .put(COL_IMPORT_DENY_PART, totImportPart)
            .put(COL_EXPORT_TAG, totExportTag)
            .put(COL_EXPORT_ACCEPT_ALL, totExportAll)
            .put(COL_EXPORT_DENY_ALL, totExportNone)
            .put(COL_EXPORT_DENY_PART, totExportPart)
            .put(COL_OTHER, totOther)
            .build());

    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(
                            COL_NODE, Schema.STRING, "Node name", true, false),
                    new ColumnMetadata(
                            COL_IMPORT_TAG, Schema.INTEGER,
                            "Import tag policies", false, true),
                    new ColumnMetadata(
                            COL_IMPORT_ACCEPT_ALL, Schema.INTEGER,
                            "Import accept-all policies", false, true),
                    new ColumnMetadata(
                            COL_IMPORT_DENY_ALL, Schema.INTEGER,
                            "Import deny-all policies", false, true),
                    new ColumnMetadata(
                            COL_IMPORT_DENY_PART, Schema.INTEGER,
                            "Import partial-deny policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_TAG, Schema.INTEGER,
                            "Export tag policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_ACCEPT_ALL, Schema.INTEGER,
                            "Export accept-all policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_DENY_ALL, Schema.INTEGER,
                            "Export deny-all policies", false, true),
                    new ColumnMetadata(
                            COL_EXPORT_DENY_PART, Schema.INTEGER,
                            "Export partial-deny policies", false, true),
                    new ColumnMetadata(
                            COL_OTHER, Schema.INTEGER,
                            "Other policies", false, true));
    return new TableMetadata(
            columns,
            String.format(
                    "Counts of routing policies categorized by content and direction on ${%s}.",
                    COL_NODE));
  }
}