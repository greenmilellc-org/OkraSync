/*
 * Copyright (c) 2019 Okra Scheduler
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package okra;

import com.mongodb.MongoClient;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import okra.base.model.OkraItem;
import okra.base.model.OkraStatus;
import okra.base.sync.AbstractOkraSync;
import okra.exception.InvalidOkraItemException;
import okra.exception.OkraItemNotFoundException;
import okra.index.IndexCreator;
import okra.serialization.DocumentSerializer;
import okra.util.DateUtil;
import okra.util.QueryUtil;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class OkraSyncImpl<T extends OkraItem> extends AbstractOkraSync<T> {

    private final MongoClient client;
    private final Class<T> scheduleItemClass;
    private final long defaultHeartbeatExpirationMillis;
    private final DocumentSerializer serializer;
    private static final Logger LOGGER = LoggerFactory.getLogger(OkraSyncImpl.class);

    public OkraSyncImpl(final MongoClient client, final String database,
                        final String collection, final Class<T> scheduleItemClass,
                        final long defaultHeartbeatExpiration,
                        final TimeUnit defaultHeartbeatExpirationUnit) {
        super(database, collection);
        this.client = client;
        this.scheduleItemClass = scheduleItemClass;
        this.defaultHeartbeatExpirationMillis = defaultHeartbeatExpirationUnit.toMillis(defaultHeartbeatExpiration);
        this.serializer = new DocumentSerializer();
        setup();
    }

    @Override
    public void setup() {
        super.setup();
        IndexCreator.ensureIndexes(this, client, getDatabase(), getCollection());
    }

    @Override
    public Optional<T> poll() {
        final Optional<T> result = peek();
        result.ifPresent(this::delete);
        return result;
    }

    @Override
    public Optional<T> peek() {
        final Document update = new Document();
        update.put("heartbeat", new Date());
        update.put("status", OkraStatus.PROCESSING.name());

        final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        options.sort(Sorts.ascending("runDate"));

        Document document = findAndUpdateDocumentByQuery(QueryUtil.pendingQuery(), update, options);
        if (document == null) {
            final Bson heartBeatQuery = QueryUtil.heartBeatQuery(defaultHeartbeatExpirationMillis / 1000);
            document = findAndUpdateDocumentByQuery(heartBeatQuery, update, options);
            if(document == null){
                return Optional.empty();
            }
        }

        return Optional.ofNullable(serializer.fromDocument(scheduleItemClass, document));
    }

    private Document findAndUpdateDocumentByQuery(Bson peekQuery,
                                                  Document update,
                                                  FindOneAndUpdateOptions options) {
        return client
                .getDatabase(getDatabase())
                .getCollection(getCollection())
                .findOneAndUpdate(peekQuery, new Document("$set", update), options);
    }

    @Override
    public T retrieve() throws OkraItemNotFoundException {
        return peek().orElseThrow(OkraItemNotFoundException::new);
    }

    @Override
    public Optional<T> reschedule(final T item) {
        validateReschedule(item);

        final Document query = new Document();
        query.put("_id", new ObjectId(item.getId()));
        query.put("heartbeat", DateUtil.toDate(item.getHeartbeat()));

        final Document setDoc = new Document();
        setDoc.put("heartbeat", null);
        setDoc.put("runDate", DateUtil.toDate(item.getRunDate()));
        setDoc.put("status", OkraStatus.PENDING.name());

        final Document update = new Document();
        update.put("$set", setDoc);

        final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);

        final Document document = client
                .getDatabase(getDatabase())
                .getCollection(getCollection())
                .findOneAndUpdate(query, update, options);

        if (document == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(serializer.fromDocument(scheduleItemClass, document));
    }

    @Override
    public Optional<T> heartbeat(final T item) {
        return heartbeatAndUpdateCustomAttrs(item, null);
    }

    @Override
    public Optional<T> heartbeatAndUpdateCustomAttrs(final T item, final Map<String, Object> attrs) {
        validateHeartbeat(item);

        final Document query = new Document();
        query.put("_id", new ObjectId(item.getId()));
        query.put("status", OkraStatus.PROCESSING.name());
        query.put("heartbeat", DateUtil.toDate(item.getHeartbeat()));

        final Document update = new Document();
        update.put("$set", new Document("heartbeat", new Date()));

        if (attrs != null && !attrs.isEmpty()) {
            attrs.forEach((key, value) -> update.append("$set", new Document(key, value)));
        }

        final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);

        final Document result = client
                .getDatabase(getDatabase())
                .getCollection(getCollection())
                .findOneAndUpdate(query, update, options);

        if (result == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(serializer.fromDocument(scheduleItemClass, result));
    }

    @Override
    public void delete(final T item) {
        final Document query = new Document("_id", new ObjectId(item.getId()));
        client.getDatabase(getDatabase()).getCollection(getCollection()).deleteOne(query);
    }

    @Override
    public void schedule(final T item) {
        validateSchedule(item);
        item.setStatus(OkraStatus.PENDING);

        final Document document = serializer.toDocument(item);

        client.getDatabase(getDatabase())
                .getCollection(getCollection())
                .insertOne(document);
    }

    @Override
    public long countByStatus(final OkraStatus status) {
        final Document query = new Document("status", status.name());

        return client
                .getDatabase(getDatabase())
                .getCollection(getCollection())
                .count(query);
    }

    @Override
    public long countDelayed() {
        final Document query = new Document();

        query.put("status", OkraStatus.PENDING.name());
        query.put("runDate", new Document("$lt", DateUtil.toDate(LocalDateTime.now())));

        return client
                .getDatabase(getDatabase())
                .getCollection(getCollection())
                .count(query);
    }

    private void validateHeartbeat(final T item) {
        if (item.getId() == null || item.getHeartbeat() == null || item.getStatus() == null) {
            throw new InvalidOkraItemException();
        }
    }

    private void validateSchedule(final T item) {
        if (item == null || item.getRunDate() == null || item.getId() != null) {
            throw new InvalidOkraItemException();
        }
    }

    private void validateReschedule(T item) {
        if (item == null
                || item.getHeartbeat() == null
                || item.getRunDate() == null
                || item.getId() == null) {
            throw new InvalidOkraItemException();
        }
    }
}