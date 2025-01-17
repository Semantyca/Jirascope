package io.kneo.projects.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kneo.core.model.user.IUser;
import io.kneo.core.repository.AsyncRepository;
import io.kneo.core.repository.exception.DocumentHasNotFoundException;
import io.kneo.core.repository.exception.DocumentModificationAccessException;
import io.kneo.core.repository.rls.RLSRepository;
import io.kneo.core.repository.table.EntityData;
import io.kneo.projects.model.Task;
import io.kneo.projects.repository.table.ProjectNameResolver;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.kneo.projects.repository.table.ProjectNameResolver.TASK;

@ApplicationScoped
public class TaskRepository extends AsyncRepository {
    private static final EntityData entityData = ProjectNameResolver.create().getEntityNames(TASK);

    private static final String BASE_REQUEST = """
            SELECT pt.*, ptr.*  FROM prj__tasks pt JOIN prj__task_readers ptr ON pt.id = ptr.entity_id\s""";

    @Inject
    public TaskRepository(PgPool client, ObjectMapper mapper, RLSRepository rlsRepository) {
        super(client, mapper, rlsRepository);
    }


    public Uni<List<Task>> getAll(final int limit, final int offset, final long userID) {
        String sql = BASE_REQUEST + "WHERE ptr.reader = " + userID;
        if (limit > 0) {
            sql += String.format(" LIMIT %s OFFSET %s", limit, offset);
        }
        return client.query(sql)
                .execute()
                .onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
                .onItem().transform(this::from)
                .collect().asList();
    }

    public Uni<Integer> getAllCount(long userID) {
        return getAllCount(userID, entityData.getTableName(), entityData.getRlsName());
    }

    public Uni<List<Task>> searchByCondition(String cond) {
        String query = String.format("SELECT * FROM %s WHERE %s ", entityData.getTableName(), cond);
        return client.query(query)
                .execute()
                .onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
                .onItem().transform(this::from)
                .collect().asList();
    }

    public Uni<Task> findById(UUID uuid, Long userID) {
        return client.preparedQuery( String.format("SELECT pt.*, ptr.*  FROM %s pt JOIN %s ptr ON pt.id = ptr.entity_id " +
                        "WHERE ptr.reader = $1 AND pt.id = $2", entityData.getTableName(), entityData.getRlsName()))
                .execute(Tuple.of(userID, uuid))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> {
                    if (iterator.hasNext()) {
                        return from(iterator.next());
                    } else {
                        LOGGER.warn(String.format("No %s found with id: " + uuid, entityData.getTableName()));
                        return null;
                    }
                });
    }


    private Task from(Row row) {
        Task doc = new Task();
        setDefaultFields(doc, row);
        doc.setStatus(row.getInteger("status"));
        doc.setBody(row.getString("body"));
        doc.setAssignee(row.getLong("assignee"));
        doc.setParent(row.getUUID("parent_id"));
        doc.setCancellationComment(row.getString("cancel_comment"));
        doc.setPriority(row.getInteger("priority"));
        doc.setProject(row.getUUID("project_id"));
        doc.setRegNumber(row.getString("reg_number"));
        LocalDate startDateTime = row.getLocalDate("start_date");
        if (startDateTime != null) {
            doc.setStartDate(startDateTime);
        }
        LocalDate targetDateTime = row.getLocalDate("target_date");
        if (targetDateTime != null) {
            doc.setTargetDate(targetDateTime);
        }
        doc.setTaskType(row.getUUID("task_type_id"));
        doc.setTitle(row.getString("title"));
        return doc;
    }

    public Uni<Task> insert(Task doc, IUser user) {
        LocalDateTime nowTime = ZonedDateTime.now().toLocalDateTime();
        String sql = String.format("INSERT INTO %s" +
                "(reg_date, author, last_mod_date, last_mod_user, assignee, body, target_date, priority, start_date, status, title, parent_id, project_id, task_type_id, reg_number, status_date, cancel_comment)" +
                "VALUES($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17) RETURNING id;", entityData.getTableName());
        Tuple params = Tuple.of(nowTime, user, nowTime, user);
        Tuple allParams = params
                .addLong(doc.getAssignee())
                .addString(doc.getBody());
        if (doc.getTargetDate() != null) {
            allParams.addLocalDate(doc.getTargetDate());
        } else {
            allParams.addLocalDateTime(null);
        }
        allParams = params
                .addInteger(doc.getPriority())
                .addLocalDate(doc.getStartDate())
                .addInteger(doc.getStatus())
                .addString(doc.getTitle())
                .addUUID(doc.getParent())
                .addUUID(doc.getProject())
                .addUUID(doc.getTaskType())
                .addString(doc.getRegNumber())
                .addLocalDate(doc.getStartDate())
                .addString(doc.getCancellationComment());
        String readersSql = String.format("INSERT INTO %s(reader, entity_id, can_edit, can_delete) VALUES($1, $2, $3, $4)", entityData.getRlsName());
        String labelsSql = "INSERT INTO prj__task_labels(id, label_id) VALUES($1, $2)";
        Tuple finalAllParams = allParams;
        return client.withTransaction(tx -> {
            return tx.preparedQuery(sql)
                    .execute(finalAllParams)
                    .onItem().transform(result -> result.iterator().next().getUUID("id"))
                    .onFailure().recoverWithUni(t ->
                            Uni.createFrom().failure(t))
                    .onItem().transformToUni(id -> {
                        return tx.preparedQuery(readersSql)
                                .execute(Tuple.of(user, id, 1, 1))
                                .onItem().ignore().andContinueWithNull()
                                .onFailure().recoverWithUni(t ->
                                        Uni.createFrom().failure(t))
                                .onItem().transform(unused -> id);
                    })
                    .onItem().transformToUni(id -> {
                        if (doc.getLabels().isEmpty()) {
                            return Uni.createFrom().item(id);
                        }
                        List<Uni<UUID>> unis = new ArrayList<>();
                        for (UUID label : doc.getLabels()) {
                            Uni<UUID> uni = tx.preparedQuery(labelsSql)
                                    .execute(Tuple.of(id, label))
                                    .onItem().ignore().andContinueWithNull()
                                    .onFailure().recoverWithUni(t ->
                                            Uni.createFrom().failure(t))
                                    .onItem().transform(unused -> label);
                            unis.add(uni);
                        }
                        return Uni.combine().all().unis(unis).with(l -> id);
                    });
        }).onItem().transformToUni(id -> findById(id, user.getId())
                .onItem().transform(task -> task));
    }

    public Uni<Task> update(UUID id, Task doc, IUser user) {
        return rlsRepository.findById(entityData.getRlsName(), user.getId(), id)
                .onItem().transformToUni(permissions -> {
                    if (permissions[0]) {
                        LocalDateTime nowTime = ZonedDateTime.now().toLocalDateTime();
                        String sql = String.format("UPDATE %s SET assignee=$1, body=$2, target_date=$3, priority=$4, " +
                                "start_date=$5, status=$6, title=$7, parent_id=$8, project_id=$9, task_type_id=$10, " +
                                "status_date=$11, cancel_comment=$12, last_mod_date=$13, last_mod_user=$14 " +
                                "WHERE id=$15;", entityData.getTableName());

                        Tuple params = Tuple.of(doc.getAssignee(), doc.getBody());

                        if (doc.getTargetDate() != null) {
                            params.addLocalDate(doc.getTargetDate());
                        } else {
                            params.addLocalDateTime(null);
                        }

                        params.addInteger(doc.getPriority());
                        if (doc.getStartDate() != null) {
                            params.addLocalDate(doc.getStartDate());
                        } else {
                            params.addLocalDateTime(null);
                        }

                        params.addInteger(doc.getStatus())
                                .addString(doc.getTitle())
                                .addUUID(doc.getParent())
                                .addUUID(doc.getProject())
                                .addUUID(doc.getTaskType())
                                .addLocalDateTime(nowTime)
                                .addString(doc.getCancellationComment())
                                .addLocalDateTime(nowTime)
                                .addLong(user.getId())
                                .addUUID(id);

                        return client.withTransaction(tx -> tx.preparedQuery(sql)
                                        .execute(params)
                                        .onItem().transformToUni(rowSet -> {
                                            int rowCount = rowSet.rowCount();
                                            if (rowCount == 0) {
                                                return Uni.createFrom().failure(new DocumentHasNotFoundException(id));
                                            }
                                            if (!doc.getLabels().isEmpty()) {
                                                String deleteLabelsSql = "DELETE FROM prj__task_labels WHERE id=$1";
                                                Uni<Void> deleteLabelsUni = tx.preparedQuery(deleteLabelsSql)
                                                        .execute(Tuple.of(id))
                                                        .onItem().ignore().andContinueWithNull();

                                                List<Uni<Void>> labelInsertUnis = new ArrayList<>();
                                                for (UUID label : doc.getLabels()) {
                                                    String labelsSql = "INSERT INTO prj__task_labels(id, label_id) VALUES($1, $2)";
                                                    Uni<Void> labelInsertUni = tx.preparedQuery(labelsSql)
                                                            .execute(Tuple.of(id, label))
                                                            .onItem().ignore().andContinueWithNull();
                                                    labelInsertUnis.add(labelInsertUni);
                                                }

                                                return deleteLabelsUni.flatMap(ignored ->
                                                        Uni.combine().all().unis(labelInsertUnis).discardItems()
                                                ).map(ignored -> rowCount);
                                            } else {
                                                return Uni.createFrom().item(rowCount);
                                            }
                                        })
                                        .onFailure().recoverWithUni(t ->
                                                Uni.createFrom().failure(t)))
                                .onItem().transformToUni(rowCount -> findById(id, user.getId())
                                        .onItem().transform(task -> task));
                    } else {
                        return Uni.createFrom()
                                .failure(new DocumentModificationAccessException("User does not have edit permission", user.getUserName(), id));
                    }
                });
    }



    public Uni<Integer> delete(UUID uuid, IUser user) {
        return delete(uuid, entityData, user);
    }

}
