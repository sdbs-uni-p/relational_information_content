# Information Content Computation in Relational Databases
This is a project to compute information contents in relational data as defined in the paper "An information-theoretic approach to normal forms for relational and XML data" by Marcelo Arenas and Leonid Libkin, published in PODS, 2003.
The information content is computed using the (information-theoretic) term of entropy.
We use information content and entropy synonymously.

The implementation approach and its theory is described in the paper "A Plaque Test for Redundancies in Relational Data" by Christoph Köhnen, Stefan Klessinger, Jens Zumbrägel and Stefanie Scherzinger,
published in the QDB workshop co-located with VLDB 2023.

This project provides a Docker container.

## Setting up the program
You can set up the package on the host system or on a docker container.
### Setup on host system
````shell
mvn clean install
cp target/relational_information_content-1.0-SNAPSHOT-jar-with-dependencies.jar relational_information_content.jar
````
### Setting up a docker container
````shell
docker build -t entropy .
docker run -it entropy
````

## Usage of the program
After the setup, the program can be executed with one of the following commands:
````shell
java -jar relational_information_content.jar <table_file> [CSV_OPTIONS] [OPTIONS] <funcDeps>
java -jar relational_information_content.jar <table_encoded> -e [OPTIONS] <funcDeps>
````
where the parameters are as follows:

* ``<table_file>``: the path to a csv file containing each row of the table comma-separated, e.g.
  ```
  1,2,3
  1,5,6
  2,7,8
  ```
* ``<table_encoded>``: the table content as a string with the rows separated by a semicolon, in the above example ``1,2,3;1,5,6;2,7,8`` (quote this string or escape the semicolon).
* ``<funcDeps>``: a list of all functional dependencies for which the calculation is supposed to be executed.
  Each column is identified as a number, the first column is 1, the second one is 2 and so on. The columns are comma-separated.
  The number of parameters is arbitrary, each parameter stands for one functional dependency (quote each functional dependency or escape ``>`` in the arrow).
  For example, the functional dependencies ``F = {2->3, 12->3}`` (the second column determines the third one and the first and second column together determine the third column)
  are typed in as ``2-\>3 1,2-\>3``, ``"2->3" "1,2->3"``, ``"2->3" 1,2-\>3`` or ``2-\>3 "1,2->3"``. Note that in this implementation the right-hand side of a functional dependency can only contain one column,
  hence ``1->23`` (first column determined the second and the third column) must be split to ``1->2, 1->3`` and typed in as ``1-\>2 1-\>3`` (or using quotes, see above).
* ``CSV_OPTIONS`` (ignored if flag ``-e`` is set):
  * ``-d <delimiter>``: the delimiter in the CSV file (default is ``,``).
  * ``--header``: consider the first line in the CSV file as header and ignore it for the entropy computation.
* ``OPTIONS``:
  * ``--name <targetfile>``: save the result of the entropy computation in a file with path <targetfile>.
  * ``--show-process``: show the ratio of processed computations.
  * ``-i``: "identify ones", enables a shortcut which identifies the output cells containing a one and omits its calculations.
  * ``-s``: "consider subtables", this parameter enables a shortcut which calculates the entropies only for subtables but obtaining the same results as in the naive computation.
  * ``-r <numberOfRuns>``: "randomized approach", these parameters enable to compute the information content of the cells using an approximative algorithm by enabling or disabling the other cells in the table randomly and do this for a number of runs fixed with the parameter ``<numberOfRuns>``.
  * ``--closure``: execute computation using the transitive closure of the given functional dependencies. For details see below.

### The parameter ``--closure``
This parameter computes the transitive closure from the given set of functional dependencies and computes the entropies based on this extended FD set.
This parameter should always be set, unless the given set is transitively closed (to reduce computation times).
Due to the implementation, computing entropies with a transitively not closed FD set can lead to incorrect results.

### Examples
Load a table from a csv file and compute entropies with two functional dependencies:
````shell
java -jar relational_information_content.jar input.csv "1->2" "3,4->1"
````
Additionally, save computed entropies in an output file:
````shell
java -jar relational_information_content.jar input.csv --name outout/example_entropies.csv "1->2" "3,4->1"
````
Additionally, use optimizations for computation (identify ones, consider subtables and randomized approach, i.e., Monte Carlo method with 100,000 iterations):
````shell
java -jar relational_information_content.jar input.csv --name outout/example_entropies.csv -i -s -r 100000 "1->2" "3,4->1"
````
Additionally, compute the transitive closure of the given set of functional dependencies (otherwise the results might not be correct regarding the theoretic framework if the given set is not transitively closed):
````shell
java -jar relational_information_content.jar input.csv --name outout/example_entropies.csv -i -s -r 100000 --closure "1->2" "3,4->1"
````
Load a table from a csv file with semicolon-separated values and header and compute entropies with two functional dependencies:
````shell
java -jar relational_information_content.jar input.csv --delimiter ';' --header "1->2" "3,4->1"
````
Compute entropies from a table given encoded as string:
````shell
java -jar relational_information_content.jar "1,2,3;4,2,3" -e "1->2" "2->3"
````
Additionally, compute the transitive closure and apply optimizations:
````shell
java -jar relational_information_content.jar "1,2,3;4,2,3" -e --closure -i -s -r 1000 "1->2" "2->3"
````

## About
To refer to this project in a publication, please use this BibTeX entry.
```bibtex
@Misc{relational_information_content,
  author  = {Christoph K{\"{o}}hnen and 
             Stefan Klessinger and 
             Jens Zumbr{\"{a}}gel and 
             Stefanie Scherzinger},
  title   = {Information Content Computation in Relational Databases},
  note    = {\url{https://github.com/sdbs-uni-p/relational_information_content}},
  year    = {2024}
}
```

This project was implemented during the master thesis of Christoph Köhnen, supervised by Stefanie Scherzinger, Jens Zumbrägel and Stefan Klessinger.

This work was partly funded by Deutsche Forschungsgemeinschaft (DFG, German Research Foundation) grant #385808805.
