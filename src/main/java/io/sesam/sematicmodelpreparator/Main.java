package io.sesam.sematicmodelpreparator;

import ch.qos.logback.classic.Level;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigSchema;
import org.eclipse.rdf4j.repository.manager.LocalRepositoryManager;
import org.eclipse.rdf4j.repository.manager.RepositoryManager;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.slf4j.LoggerFactory;

/**
 *
 * @author 100tsa
 */
public class Main {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Main.class);
    /*Default GraphDB config*/
    private static final String CONFIG = "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n"
            + "@prefix rep: <http://www.openrdf.org/config/repository#> .\n"
            + "@prefix sail: <http://www.openrdf.org/config/sail#> .\n"
            + "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n"
            + "\n"
            + "<#blah-blah> a rep:Repository;\n"
            + "  rep:repositoryID \"graphdb-repo\";\n"
            + "  rep:repositoryImpl [\n"
            + "      rep:repositoryType \"graphdb:FreeSailRepository\";\n"
            + "      <http://www.openrdf.org/config/repository/sail#sailImpl> [\n"
            + "          <http://www.ontotext.com/trree/owlim#base-URL> \"http://example.org/owlim#\";\n"
            + "          <http://www.ontotext.com/trree/owlim#check-for-inconsistencies> \"false\";\n"
            + "          <http://www.ontotext.com/trree/owlim#defaultNS> \"\";\n"
            + "          <http://www.ontotext.com/trree/owlim#disable-sameAs> \"true\";\n"
            + "          <http://www.ontotext.com/trree/owlim#enable-context-index> \"false\";\n"
            + "          <http://www.ontotext.com/trree/owlim#enable-literal-index> \"true\";\n"
            + "          <http://www.ontotext.com/trree/owlim#enablePredicateList> \"true\";\n"
            + "          <http://www.ontotext.com/trree/owlim#entity-id-size> \"32\";\n"
            + "          <http://www.ontotext.com/trree/owlim#entity-index-size> \"10000000\";\n"
            + "          <http://www.ontotext.com/trree/owlim#imports> \"\";\n"
            + "          <http://www.ontotext.com/trree/owlim#in-memory-literal-properties> \"true\";\n"
            + "          <http://www.ontotext.com/trree/owlim#query-limit-results> \"0\";\n"
            + "          <http://www.ontotext.com/trree/owlim#query-timeout> \"0\";\n"
            + "          <http://www.ontotext.com/trree/owlim#read-only> \"false\";\n"
            + "          <http://www.ontotext.com/trree/owlim#repository-type> \"file-repository\";\n"
            + "          <http://www.ontotext.com/trree/owlim#ruleset> \"rdfsplus-optimized\";\n"
            + "          <http://www.ontotext.com/trree/owlim#storage-folder> \"storage\";\n"
            + "          <http://www.ontotext.com/trree/owlim#throw-QueryEvaluationException-on-timeout> \"false\";\n"
            + "          sail:sailType \"graphdb:FreeSail\"\n"
            + "        ]\n"
            + "    ];\n"
            + "  rdfs:label \"\" .";

    public static void main(String[] args) throws IOException {
        ch.qos.logback.classic.Logger rootL = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootL.setLevel(Level.INFO);

        if (args.length < 2) {
            throw new IllegalArgumentException("Not enough arguments provided. "
                    + "Usage: java -jar make_some_magic.jar <path to core files> <name for output file>");
        }
        String pathToCoreFiles = args[0];
        String outputFileName = args[1];
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**.ttl");

        Path coreFilesDir = Paths.get(".", pathToCoreFiles);

        Path outputFile = Files.createFile(Paths.get(".", outputFileName));
        FileOutputStream outFileOutStream = new FileOutputStream(outputFile.toFile());
        RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, outFileOutStream);

        RepositoryManager repositoryManager = new LocalRepositoryManager(new File("."));
        repositoryManager.init();

        TreeModel graph = new TreeModel();

        try ( InputStream config = IOUtils.toInputStream(CONFIG, Charset.defaultCharset());) {
            RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);
            rdfParser.setRDFHandler(new StatementCollector(graph));
            rdfParser.parse(config, RepositoryConfigSchema.NAMESPACE);
        }

        Resource repositoryNode = Models.subject(
                graph.filter(null, RDF.TYPE, RepositoryConfigSchema.REPOSITORY))
                .orElseThrow(() -> new RuntimeException(
                "Oops, no <http://www.openrdf.org/config/repository#> subject found!"));

        RepositoryConfig repositoryConfig = RepositoryConfig.create(graph, repositoryNode);
        repositoryManager.addRepositoryConfig(repositoryConfig);

        Repository repository = repositoryManager.getRepository("graphdb-repo");

        try ( RepositoryConnection repositoryConnection = repository.getConnection();  DirectoryStream<Path> stream
                = Files.newDirectoryStream(coreFilesDir)) {
            
            for (Path p : stream) {
                try {
                    if (!Files.isDirectory(p) && matcher.matches(p)) {
                        LOG.info("processing file {}", p.getFileName());
                        repositoryConnection.add(p.toFile(), null, RDFFormat.TURTLE);
                    }

                } catch (IOException | RDFParseException | UnsupportedRDFormatException exc) {
                    LOG.warn("Couldn't parse file due to {}", exc.getMessage());
                }
            }
            
            repositoryConnection.export(writer);
        }
        repository.shutDown();
        repositoryManager.shutDown();

        FileUtils.deleteDirectory(new File("repositories"));

    }
}
