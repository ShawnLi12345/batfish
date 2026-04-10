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

