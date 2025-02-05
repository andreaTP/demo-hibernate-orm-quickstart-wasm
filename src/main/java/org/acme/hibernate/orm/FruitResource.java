package org.acme.hibernate.orm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import io.agroal.api.AgroalDataSource;
import io.roastedroot.sqlite4j.Function;
import io.roastedroot.sqlite4j.SQLiteConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.internal.SessionImpl;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("fruits")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class FruitResource {

    private static final Logger LOGGER = Logger.getLogger(FruitResource.class.getName());
    private static final String UDFNAME = "myudf";

    @Inject
    EntityManager entityManager;

    @Inject
    JavascriptService jsService;

    @GET
    public List<Fruit> get() {
        return entityManager.createNamedQuery("Fruits.findAll", Fruit.class)
                .getResultList();
    }

    @POST
    @Path("register")
    public Response registerUdf(String jsCode) throws SQLException {
        LOGGER.info("registered UDF: " + jsCode);

        JdbcConnectionAccess access = entityManager
                .unwrap(SessionImpl.class)
                .getJdbcConnectionAccess();
        try (var conn = access.obtainConnection()) {
            SQLiteConnection sqliteConn = conn.unwrap(SQLiteConnection.class);
            Function.create(sqliteConn, UDFNAME, new Function() {
                @Override
                public void xFunc() throws SQLException {
                    String fruitName = value_text(0);

                    LOGGER.info("UDF called on name: " + fruitName);

                    result(jsService.compute(fruitName));
                }
            });
        }
        return Response.ok().status(200).build();
    }

    @GET
    @Path("udf/{id}")
    public int udfInvoke(Integer id) throws SQLException {
        var result = (int) entityManager
                .createNativeQuery("select " + UDFNAME + "(f.name) from known_fruits f where f.id = " + id)
                .getSingleResult();
        return result;
    }


    @GET
    @Path("{id}")
    public Fruit getSingle(Integer id) {
        Fruit entity = entityManager.find(Fruit.class, id);
        if (entity == null) {
            throw new WebApplicationException("Fruit with id of " + id + " does not exist.", 404);
        }
        return entity;
    }

    @POST
    @Transactional
    public Response create(Fruit fruit) {
        if (fruit.getId() != null) {
            throw new WebApplicationException("Id was invalidly set on request.", 422);
        }

        entityManager.persist(fruit);
        return Response.ok(fruit).status(201).build();
    }

    @PUT
    @Path("{id}")
    @Transactional
    public Fruit update(Integer id, Fruit fruit) {
        if (fruit.getName() == null) {
            throw new WebApplicationException("Fruit Name was not set on request.", 422);
        }

        Fruit entity = entityManager.find(Fruit.class, id);

        if (entity == null) {
            throw new WebApplicationException("Fruit with id of " + id + " does not exist.", 404);
        }

        entity.setName(fruit.getName());

        return entity;
    }

    @DELETE
    @Path("{id}")
    @Transactional
    public Response delete(Integer id) {
        Fruit entity = entityManager.getReference(Fruit.class, id);
        if (entity == null) {
            throw new WebApplicationException("Fruit with id of " + id + " does not exist.", 404);
        }
        entityManager.remove(entity);
        return Response.status(204).build();
    }

    @Provider
    public static class ErrorMapper implements ExceptionMapper<Exception> {

        @Inject
        ObjectMapper objectMapper;

        @Override
        public Response toResponse(Exception exception) {
            LOGGER.error("Failed to handle request", exception);

            int code = 500;
            if (exception instanceof WebApplicationException) {
                code = ((WebApplicationException) exception).getResponse().getStatus();
            }

            ObjectNode exceptionJson = objectMapper.createObjectNode();
            exceptionJson.put("exceptionType", exception.getClass().getName());
            exceptionJson.put("code", code);

            if (exception.getMessage() != null) {
                exceptionJson.put("error", exception.getMessage());
            }

            return Response.status(code)
                    .entity(exceptionJson)
                    .build();
        }

    }
}
