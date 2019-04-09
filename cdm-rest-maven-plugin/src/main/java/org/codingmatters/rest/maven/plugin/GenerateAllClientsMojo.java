package org.codingmatters.rest.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codingmatters.rest.api.generator.ClientHandlerImplementation;
import org.codingmatters.rest.api.generator.ClientInterfaceGenerator;
import org.codingmatters.rest.api.generator.ClientRequesterImplementation;
import org.codingmatters.rest.api.generator.exception.RamlSpecException;
import org.codingmatters.rest.js.api.client.JSClientGenerator;
import org.codingmatters.rest.php.api.client.PhpClientRequesterGenerator;
import org.codingmatters.rest.php.api.client.generator.ComposerFileGenerator;
import org.codingmatters.rest.php.api.client.model.ApiGeneratorPhp;
import org.codingmatters.rest.php.api.client.model.ApiTypesPhpGenerator;
import org.codingmatters.value.objects.js.error.ProcessingException;
import org.codingmatters.value.objects.js.generator.GenerationException;
import org.codingmatters.value.objects.php.generator.SpecPhpGenerator;
import org.codingmatters.value.objects.spec.Spec;
import org.raml.v2.api.RamlModelResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Mojo(name = "generate-all-clients")
public class GenerateAllClientsMojo extends AbstractGenerateAPIMojo {

    @Parameter(defaultValue = "${basedir}/target")
    private File outputDirectory;

    @Parameter(required = true, alias = "root-package")
    private String rootPackage;

    @Parameter(required = true)
    private String vendor;

    @Parameter(defaultValue = "${project.artifactId}")
    private String artifactId;

    @Parameter(defaultValue = "${project.version}")
    private String version;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        RamlModelResult ramlModel = this.resolveRamlModel();
        generateJSClient( ramlModel );
        generateJavaClient( ramlModel );
        generatePhpClient( ramlModel );
    }

    private void generatePhpClient( RamlModelResult ramlModel ) throws MojoExecutionException {
        File phpOutputDirectory = new File( outputDirectory, "php-generated-client" );
        boolean useTypeHintingReturnValue = false;
        String clientPackage = this.rootPackage + ".client";
        String apiPackage = this.rootPackage + ".api";
        String typesPackage = this.rootPackage + ".api.types";
        try {
            Spec spec = new ApiTypesPhpGenerator( typesPackage ).generate( ramlModel );
            new SpecPhpGenerator( spec, typesPackage, phpOutputDirectory, useTypeHintingReturnValue ).generate();
        } catch( RamlSpecException | IOException e ){
            throw new MojoExecutionException( "Error generating php client", e );
        }
        try {
            Spec spec = new ApiGeneratorPhp( typesPackage ).generate( ramlModel );
            new SpecPhpGenerator( spec, apiPackage, phpOutputDirectory, useTypeHintingReturnValue ).generate();
        } catch( RamlSpecException | IOException e ){
            throw new MojoExecutionException( "Error generating php client", e );
        }
        try {
            PhpClientRequesterGenerator requesterGenerator = new PhpClientRequesterGenerator( clientPackage, apiPackage, typesPackage, phpOutputDirectory, useTypeHintingReturnValue );
            requesterGenerator.generate( ramlModel );
        } catch( RamlSpecException | IOException e ){
            throw new MojoExecutionException( "Error generating php client", e );
        }
        try {
            ComposerFileGenerator requesterGenerator = new ComposerFileGenerator( phpOutputDirectory, vendor, artifactId, version );
            requesterGenerator.generateComposerFile();
        } catch( Exception e ){
            throw new MojoExecutionException( "Error generating php client", e );
        }
        try {
            zipPhpClient( phpOutputDirectory );
        } catch( Exception e ){
            throw new MojoExecutionException( "Error generating php client", e );
        }

    }

    private void zipPhpClient( File fileToZip ) throws IOException {
        try( FileOutputStream fos = new FileOutputStream( new File( fileToZip.getParentFile(), "php-generated-client.zip" ) ) ) {
            try( ZipOutputStream zipOut = new ZipOutputStream( fos ) ) {
                zipFile( fileToZip, null, zipOut );
            }
        }
    }

    private void generateJavaClient( RamlModelResult ramlModel ) throws MojoExecutionException {
        String destinationPackage = rootPackage + ".client";
        String typesPackage = rootPackage + ".api.types";
        String apiPackage = rootPackage + ".api";
        File javaOutputDirectory = new File( this.outputDirectory, "generated-sources" );
        try {
            new ClientInterfaceGenerator( destinationPackage, apiPackage, javaOutputDirectory ).generate( ramlModel );
        } catch( IOException e ){
            throw new MojoExecutionException( "error generating client interface from raml model", e );
        }
        try {
            new ClientRequesterImplementation( destinationPackage, apiPackage, typesPackage, javaOutputDirectory ).generate( ramlModel );
        } catch( IOException e ){
            throw new MojoExecutionException( "error generating requester client implementation from raml model", e );
        }
        try {
            new ClientHandlerImplementation( destinationPackage, apiPackage, typesPackage, javaOutputDirectory ).generate( ramlModel );
        } catch( IOException e ){
            throw new MojoExecutionException( "error generating handler client implementation from raml model", e );
        }
    }

    private void generateJSClient( RamlModelResult ramlModel ) throws MojoExecutionException {
        try {
            File jsOutputDir = new File( this.outputDirectory, "js-generated-client" );
            JSClientGenerator generator = new JSClientGenerator( jsOutputDir, rootPackage, vendor, artifactId, version );
            generator.generateClientApi( ramlModel );
        } catch( ProcessingException | GenerationException e ){
            throw new MojoExecutionException( "Error generating JS client", e );
        }
    }

    private static void zipFile( File fileToZip, String fileName, ZipOutputStream zipOut ) throws IOException {
        if( fileToZip.isHidden() ){
            return;
        }
        if( fileToZip.isDirectory() ){
            if( fileName != null ){
                if( fileName.endsWith( "/" ) ){
                    zipOut.putNextEntry( new ZipEntry( fileName ) );
                    zipOut.closeEntry();
                } else {
                    zipOut.putNextEntry( new ZipEntry( fileName + "/" ) );
                    zipOut.closeEntry();
                }
            }
            File[] children = fileToZip.listFiles();
            for( File childFile : children ){
                if( fileName!=null ){
                    zipFile( childFile, fileName + "/" + childFile.getName(), zipOut );
                }else{
                    zipFile( childFile, childFile.getName(), zipOut );
                }
            }
            return;
        }
        FileInputStream fis = new FileInputStream( fileToZip );
        ZipEntry zipEntry = new ZipEntry( fileName );
        zipOut.putNextEntry( zipEntry );
        byte[] bytes = new byte[1024];
        int length;
        while( (length = fis.read( bytes )) >= 0 ){
            zipOut.write( bytes, 0, length );
        }
        fis.close();
    }

}
