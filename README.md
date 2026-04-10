## How to run this forked version of batfish


#### 1. Run the Batfish service
Pull and run the latest docker container.

    docker pull shawnli12345/batfish-fork-bgp
   
    docker run --name batfish-fork-bgp -p 9997:9997 -p 9996:9996 shawnli12345/batfish-fork-bgp

The second command starts the Batfish service and maps the necessary TCP ports.

#### 2. Install Pybatfish

To analyze your network configurations, you also need [Pybatfish](https://www.github.com/batfish/pybatfish), a Python 3 SDK to interact with the Batfish service. Though not strictly necessary, we recommend that you install Pybatfish in a [virtual environment](https://docs.python.org/3/library/venv.html).

To install Pybatfish run the following commands (in a virtual environment if applicable):

    python3 -m pip install --upgrade pybatfish

#### 3. Develop your analysis

After installing Pybatfish, use your Python environment of choice (e.g., PyCharm, interactive Python shell, Jupyter, ..) to interact with Batfish. The [notebooks](https://github.com/batfish/pybatfish/tree/master/jupyter_notebooks) provide examples of such scripts.

See complete documentation of Pybatfish on [readthedocs](https://pybatfish.readthedocs.io/en/latest/).

**A new questions test notebook test_notebook.ipynb can be found at the root folder of this repo.**

#### New Questions
- **CommunityMatchUsage** (`communitymatchusage/`) — Lists number of community match expressions each nodes uses.
- **CommunityReferences** (`communitymatchusage/`) — Lists community match expressions and the nodes that reference them.
- **RouteFilterUsers** (`routefilters/`) — Lists number of route filters each node uses.
- **TiebreakerUsage** (`routepolicyproperties/`) — Lists the BGP tiebreaker used by each node.
- **MultipathMatchUsage** (`routepolicyproperties/`) — Lists nodes by their multipath match mode. Nodes without both EBGP and IBGP multipath enabled are grouped under N/A.
- **AsPathUsage** (`routepolicyproperties/`) — Lists AS paths and the nodes that have routes using that path.
- **BgpPropertiesCount** (`routepolicyproperties/`) — Counts BGP process properties across nodes.
- **CommunitySetRegex** (`routepolicyproperties/`) — Lists community set match expression names and corresponding regular expressions.
- **SubnetworkPolicies** (`routepolicyproperties/`) — Lists routing policies between routers within a subnetwork.
- **SubnetworkPolicyMetrics** (`routepolicyproperties/`) — Counts BGP properties in routing policies between routers within a subnetwork.

All paths relative to `projects/question/src/main/java/org/batfish/question/`.

