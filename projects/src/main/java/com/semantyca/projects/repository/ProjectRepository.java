package com.semantyca.projects.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semantyca.core.model.Language;
import com.semantyca.core.model.constants.ProjectStatusType;
import com.semantyca.core.repository.Repository;
import com.semantyca.projects.dto.ProjectDTO;
import com.semantyca.projects.model.Project;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProjectRepository extends Repository {

    @Inject
    PgPool client;

    @Inject
    ObjectMapper mapper;

    public Uni<List<ProjectDTO>> getAll(final int limit, final int offset, final long userID) {
        String sql = "SELECT * FROM prj__projects p, prj__project_readers ppr WHERE p.id = ppr.entity_id AND ppr.readers = " + userID;
        if (limit > 0 ) {
            sql += String.format(" LIMIT %s OFFSET %s", limit, offset);
        }
        return client.query(sql)
                .execute()
                .onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
                .onItem().transform(row -> new ProjectDTO(
                        row.getUUID("id"),
                        row.getString("name"),
                        ProjectStatusType.valueOf(row.getString("status")),
                        row.getLocalDate("finish_date"),""))
                        .collect().asList();
    }

    public Uni<Optional<Project>> findById(UUID uuid, Long userID) {
        return client.preparedQuery(  "SELECT * FROM prj__projects p, prj__project_readers ppr WHERE p.id = ppr.entity_id  AND p.id = $1 AND ppr.readers = $2")
                .execute(Tuple.of(uuid, userID))
                .onItem().transform(RowSet::iterator)
                //.onItem().delayIt().by(Duration.ofSeconds(5))
                .onItem().transform(iterator -> iterator.hasNext() ? Optional.of(from(iterator.next())) : Optional.empty());
    }

    public Optional<Project> findByValue(String base) {
        return null;
    }

    private Project from(Row row) {
        return new Project.Builder()
                .setId(row.getUUID("id"))
                .setName(row.getString("name"))
                .setStatus(ProjectStatusType.valueOf(row.getString("status")))
                .setFinishDate(row.getLocalDate("finish_date"))
                .setPosition(999)
                .setPrimaryLang(new Language.Builder().build())
                .setManager(row.getInteger("manager"))
                .setCoder(row.getInteger("programmer"))
                .setTester(row.getInteger("tester"))
                .build();
    }

    public UUID insert(Project node, Long user) {

        return node.getId();
    }


    public Language update(Language node) {

        return node;
    }

    public int delete(Long id) {

        return 1;
    }
}
