package org.codingmatters.rest.api.generator;

import com.fasterxml.jackson.core.JsonFactory;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.codingmatters.rest.api.client.Requester;
import org.codingmatters.rest.api.client.RequesterFactory;
import org.codingmatters.rest.api.client.ResponseDelegate;
import org.codingmatters.rest.api.generator.client.ResourceNaming;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.codingmatters.value.objects.generation.GenerationUtils.packageDir;
import static org.codingmatters.value.objects.generation.GenerationUtils.writeJavaFile;

public class ClientRequesterImplementation {
    private final String clientPackage;
    private final String apiPackage;
    private final File dir;

    private final ResourceNaming naming;

    public ClientRequesterImplementation(String clientPackage, String apiPackage, File dir) {
        this.clientPackage = clientPackage;
        this.apiPackage = apiPackage;
        this.dir = dir;
        this.naming = new ResourceNaming(this.apiPackage, this.resourcePackage());
    }

    public void generate(RamlModelResult model) throws IOException {
        ClassName clientInterface = ClassName.get(this.clientPackage, this.naming.type(model.getApiV10().title().value(), "Client"));
        TypeSpec clientClass = this.clientClass(clientInterface, model);
        writeJavaFile(
                packageDir(this.dir, this.clientPackage),
                this.clientPackage,
                clientClass);

        List<TypeSpec> resources = this.resourceClasses(clientInterface, model.getApiV10().resources());
        for (TypeSpec resource : resources) {
            writeJavaFile(
                    packageDir(this.dir, this.clientPackage),
                    this.resourcePackage(),
                    resource);
        }

    }

    private String resourcePackage() {
        return this.clientPackage + ".resources";
    }

    private TypeSpec clientClass(ClassName clientInterface, RamlModelResult model) {
        TypeSpec.Builder result = TypeSpec.classBuilder(this.naming.type(model.getApiV10().title().value() , "RequesterClient"))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(clientInterface)
                ;

        this.addResourceConstructor(result, model.getApiV10().resources());
        this.addChildResourcesMethods(clientInterface, model.getApiV10().resources(), result);


        return result.build();
    }


    private List<TypeSpec> resourceClasses(ClassName parentInterface, List<Resource> resources) {
        List<TypeSpec> results = new LinkedList<>();
        for (Resource resource : resources) {
            ClassName clientInterface = parentInterface.nestedClass(this.naming.resourceType(resource));
            TypeSpec resourceClass = this.resourceClass(clientInterface, resource);
            results.add(resourceClass);
            results.addAll(this.resourceClasses(clientInterface, resource.resources()));
        }

        return results;
    }

    private TypeSpec resourceClass(ClassName clientInterface, Resource resource) {
        TypeSpec.Builder result = TypeSpec.classBuilder(this.naming.type(resource.displayName().value(), "Client"))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(clientInterface)
                ;
        this.addResourceConstructor(result, resource.resources());

        for (Method method : resource.methods()) {
            this.addMethodCallMethod(result, method);
        }

        this.addChildResourcesMethods(clientInterface, resource.resources(), result);

        return result.build();
    }

    private void addMethodCallMethod(TypeSpec.Builder result, Method method) {
        ClassName requestTypeName = this.naming.methodRequestType(method);
        ClassName responseTypeName = this.naming.methodResponseType(method);

        MethodSpec.Builder caller = MethodSpec.methodBuilder(this.naming.property(method.method()))
                .addModifiers(Modifier.PUBLIC)
                .addParameter(requestTypeName, "request")
                .returns(responseTypeName)
                .addException(IOException.class);

        caller.addStatement("$T requester = this.requesterFactory\n" +
                ".forBaseUrl(this.baseUrl)\n" +
                ".path($S)",
                Requester.class, method.resource().resourcePath());

        caller.addStatement("$T response = requester.get()", ResponseDelegate.class);

        caller.addStatement("$T.Builder resp = $T.builder()",
                this.naming.methodResponseType(method),
                this.naming.methodResponseType(method));
        caller.addStatement("return resp.build()");


        result.addMethod(caller.build());
    }

    private void addResourceConstructor(TypeSpec.Builder result, List<Resource> childResources) {
        result
                .addField(ClassName.get(RequesterFactory.class), "requesterFactory", Modifier.PRIVATE, Modifier.FINAL)
                .addField(ClassName.get(JsonFactory.class), "jsonFactory", Modifier.PRIVATE, Modifier.FINAL)
                .addField(ClassName.get(String.class), "baseUrl", Modifier.PRIVATE, Modifier.FINAL);

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(RequesterFactory.class), "requesterFactory")
                .addParameter(ClassName.get(JsonFactory.class), "jsonFactory")
                .addParameter(ClassName.get(String.class), "baseUrl")
                .addStatement("this.requesterFactory = requesterFactory")
                .addStatement("this.jsonFactory = jsonFactory")
                .addStatement("this.baseUrl = baseUrl");

        for (Resource childResource : childResources) {
            ClassName childResourceType = this.naming.resourceClientType(childResource);
            String childResourceDelegate = this.naming.resourceDelegateName(childResource);

            result.addField(FieldSpec.builder(childResourceType, childResourceDelegate)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build());
            constructorBuilder.addStatement("this.$N = new $T(this.requesterFactory, this.jsonFactory, this.baseUrl)", childResourceDelegate, childResourceType);
        }

        result.addMethod(constructorBuilder.build());
    }


    private void addChildResourcesMethods(ClassName clientInterface, List<Resource> childResources, TypeSpec.Builder result) {
        for (Resource childResource : childResources) {
            result.addMethod(MethodSpec.methodBuilder(this.naming.property(childResource.displayName().value()))
                    .addModifiers( Modifier.PUBLIC)
                    .returns(clientInterface.nestedClass(this.naming.resourceType(childResource)))
                    .addStatement("return this.$N", this.naming.resourceDelegateName(childResource))
                    .build());
        }
    }

}
