package org.codingmatters.http.api.generator;

import org.codingmatters.http.api.generator.exception.RamlSpecException;
import org.codingmatters.http.api.generator.type.RamlType;
import org.codingmatters.value.objects.spec.*;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.v10.bodies.Response;
import org.raml.v2.api.model.v10.datamodel.ArrayTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;

/**
 * Created by nelt on 5/2/17.
 */
public class ApiGenerator {

    private final String typesPackage;

    public ApiGenerator(String typesPackage) {
        this.typesPackage = typesPackage;
    }

    public Spec generate(RamlModelResult ramlModel) throws RamlSpecException {
        Spec.Builder result = Spec.spec();
        for (Resource resource : ramlModel.getApiV10().resources()) {
            this.generateResourceValues(result, resource);
        }

        return result.build();
    }

    private void generateResourceValues(Spec.Builder result, Resource resource) throws RamlSpecException {
        for (Method method : resource.methods()) {
            result.addValue(this.generateMethodRequestValue(resource, method));
            result.addValue(this.generateMethodResponseValue(resource, method));
        }
        for (Resource subResource : resource.resources()) {
            this.generateResourceValues(result, subResource);
        }
    }

    private ValueSpec generateMethodRequestValue(Resource resource, Method method) throws RamlSpecException {
        ValueSpec.Builder result = ValueSpec.valueSpec()
                .name(resource.displayName().value() + this.upperCaseFirst(method.method()) + "Request");

        for (TypeDeclaration typeDeclaration : method.queryParameters()) {
            this.addPropertyFromTypeDeclaration(result, typeDeclaration);
        }
        for (TypeDeclaration typeDeclaration : method.headers()) {
            this.addPropertyFromTypeDeclaration(result, typeDeclaration);
        }
        if(method.body() != null && ! method.body().isEmpty()) {
            result.addProperty(PropertySpec.property()
                    .name("payload")
                    .type(PropertyTypeSpec.type()
                            .cardinality(PropertyCardinality.SINGLE)
                            .typeKind(TypeKind.JAVA_TYPE)
                            .typeRef(this.typesPackage + "." + method.body().get(0).type())
                    )
            );
        }
        for (TypeDeclaration typeDeclaration : resource.uriParameters()) {
            this.addPropertyFromTypeDeclaration(result, typeDeclaration);
        }


        return result.build();
    }

    private ValueSpec generateMethodResponseValue(Resource resource, Method method) throws RamlSpecException {
        ValueSpec.Builder result = ValueSpec.valueSpec()
                .name(resource.displayName().value() + this.upperCaseFirst(method.method()) + "Response");

        for (Response response : method.responses()) {
            AnonymousValueSpec.Builder responseSpec = AnonymousValueSpec.anonymousValueSpec();
            for (TypeDeclaration typeDeclaration : response.headers()) {
                responseSpec.addProperty(PropertySpec.property()
                        .name(this.lowerCaseFirst(typeDeclaration.name()))
                        .type(this.typeSpecFromDeclaration(typeDeclaration))
                        .build());
            }
            if(response.body() != null && ! response.body().isEmpty()) {
                responseSpec.addProperty(PropertySpec.property()
                        .name("payload")
                        .type(PropertyTypeSpec.type()
                                .cardinality(PropertyCardinality.SINGLE)
                                .typeKind(TypeKind.JAVA_TYPE)
                                .typeRef(this.typesPackage + "." + response.body().get(0).type())
                        )
                );
            }
            PropertySpec.Builder responseProp = PropertySpec.property()
                    .name("status" + response.code().value())
                    .type(PropertyTypeSpec.type()
                            .typeKind(TypeKind.EMBEDDED)
                            .cardinality(PropertyCardinality.SINGLE)
                            .embeddedValueSpec(responseSpec)
                    )
                    ;

            result.addProperty(responseProp);
        }


        return result.build();
    }

    private void addPropertyFromTypeDeclaration(ValueSpec.Builder result, TypeDeclaration typeDeclaration) throws RamlSpecException {
        result.addProperty(PropertySpec.property()
                .name(this.lowerCaseFirst(typeDeclaration.name()))
                .type(this.typeSpecFromDeclaration(typeDeclaration))
                .build());
    }

    private PropertyTypeSpec.Builder typeSpecFromDeclaration(TypeDeclaration typeDeclaration) throws RamlSpecException {
        PropertyTypeSpec.Builder typeSpec = PropertyTypeSpec.type();
        if(typeDeclaration.type().equals("array")) {
            typeSpec.cardinality(PropertyCardinality.LIST)
                    .typeKind(TypeKind.JAVA_TYPE)
                    .typeRef(RamlType.from(((ArrayTypeDeclaration)typeDeclaration).items()).javaType());
        } else {
            typeSpec.cardinality(PropertyCardinality.SINGLE)
                    .typeKind(TypeKind.JAVA_TYPE)
                    .typeRef(RamlType.from(typeDeclaration).javaType());
        }
        return typeSpec;
    }

    private String upperCaseFirst(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    private String lowerCaseFirst(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
}