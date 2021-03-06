package org.codingmatters.rest.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codingmatters.rest.js.api.client.JSClientGenerator;
import org.raml.v2.api.RamlModelResult;

import java.io.File;

@Mojo(name = "generate-js-client")
public class GenerateJSClientMojo extends AbstractGenerateAPIMojo {

    @Parameter(required = true, alias = "root-package")
    private String rootPackage;

    @Parameter(defaultValue = "${basedir}/target/generated-sources/")
    private File outputDirectory;

    @Parameter(required = true)
    private String vendor;

    @Parameter(defaultValue = "${project.artifactId}")
    private String artifactId;

    @Parameter(defaultValue = "${project.version}")
    private String version;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            RamlModelResult ramlModel = this.resolveRamlModel();
            JSClientGenerator generator = new JSClientGenerator(
                    this.outputDirectory,
                    this.rootPackage + ".client",
                    this.rootPackage + ".api.types",
                    this.rootPackage + ".types",
                    this.vendor,
                    this.artifactId,
                    this.version );
            generator.generateClientApi( ramlModel );
        } catch( Exception e ) {
            throw new MojoFailureException( "Error generating JS client", e );
        }
    }
}
