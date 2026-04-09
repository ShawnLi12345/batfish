package org.batfish.question.routepolicyproperties;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;

import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class SubnetworkPoliciesAnswerer extends Answerer {

  public static final String COL_NODE = "Node";
  public static final String COL_POLICY_NAME = "Policy_Name";
  public static final String COL_POLICY_TYPE = "Policy_Type";
  public static final String COL_POLICY_DEFINITION = "Policy_Definition";

  public SubnetworkPoliciesAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    SubnetworkPoliciesQuestion question = (SubnetworkPoliciesQuestion) _question;
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

  @VisibleForTesting
  static List<Row> getAnswerRows(
          SpecifierContext ctxt,
          NodeSpecifier nodeSpecifier,
          Map<String, ColumnMetadata> columnMap) {
    ImmutableList.Builder<Row> rows = ImmutableList.builder();

    Map<Ip, String> IDToNode = new TreeMap<>();
    for (String node : nodeSpecifier.resolve(ctxt)){
      Configuration config = ctxt.getConfigs().get(node);
      BgpProcess bProcess = config.getDefaultVrf().getBgpProcess();
      if(bProcess==null)continue;
      IDToNode.put(bProcess.getRouterId(), node);
    }

    for(String node : nodeSpecifier.resolve(ctxt)){
      Configuration config = ctxt.getConfigs().get(node);
      BgpProcess bProcess = config.getDefaultVrf().getBgpProcess();
      if(bProcess == null)continue;

      Set<String> iPolicies = new TreeSet<>(), ePolicies = new TreeSet<>();
      for(BgpActivePeerConfig peer : bProcess.getActiveNeighbors().values()){
        Ip remoteIp = peer.getPeerAddress();
        if(IDToNode.containsKey(remoteIp)){
          Ipv4UnicastAddressFamily af = peer.getIpv4UnicastAddressFamily();
          if(af == null)continue;
          String iPolicy = af.getImportPolicySources().isEmpty() ? null : af.getImportPolicySources().iterator().next();
          String ePolicy = af.getExportPolicySources().isEmpty() ? null : af.getExportPolicySources().iterator().next();
          if(iPolicy != null && !iPolicy.startsWith("~"))iPolicies.add(iPolicy);
          if(ePolicy != null && !ePolicy.startsWith("~"))ePolicies.add(ePolicy);
        }
      }

      for (String iPolicy : iPolicies){
        String json = BatfishObjectMapper.writeStringRuntimeError(config.getRoutingPolicies().get(iPolicy));
        if (json != null) {
          rows.add(
                  Row.builder(columnMap)
                          .put(COL_NODE, node)
                          .put(COL_POLICY_TYPE, "Import_Routing_Policy")
                          .put(COL_POLICY_NAME, iPolicy)
                          .put(COL_POLICY_DEFINITION, json)
                          .build());
        }
      }
      for (String ePolicy : ePolicies){
        String json = BatfishObjectMapper.writeStringRuntimeError(config.getRoutingPolicies().get(ePolicy));
        if (json != null) {
          rows.add(
                  Row.builder(columnMap)
                          .put(COL_NODE, node)
                          .put(COL_POLICY_TYPE, "Export_Routing_Policy")
                          .put(COL_POLICY_NAME, ePolicy)
                          .put(COL_POLICY_DEFINITION, json)
                          .build());
        }
      }
    }
    return rows.build();
  }

  static TableMetadata createTableMetadata() {
    List<ColumnMetadata> columns =
            ImmutableList.of(
                    new ColumnMetadata(
                            COL_NODE, Schema.STRING, "Node name", true, false),
                    new ColumnMetadata(
                            COL_POLICY_NAME, Schema.STRING, "Routing policy name", true, false),
                    new ColumnMetadata(
                            COL_POLICY_TYPE,
                            Schema.STRING,
                            "Import or Export routing policy",
                            false,
                            true),
                    new ColumnMetadata(
                            COL_POLICY_DEFINITION,
                            Schema.STRING,
                            "Definition of routing policy",
                            false,
                            true));
    return new TableMetadata(
            columns,
            String.format(
                    "Routing policies applied to BGP peers within the subnetwork of ${%s}.",
                    COL_NODE));
  }
}
