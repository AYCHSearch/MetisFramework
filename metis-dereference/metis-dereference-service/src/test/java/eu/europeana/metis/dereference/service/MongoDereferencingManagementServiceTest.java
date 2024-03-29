package eu.europeana.metis.dereference.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mongodb.MongoClient;
import eu.europeana.metis.cache.redis.RedisProvider;
import eu.europeana.metis.dereference.ContextualClass;
import eu.europeana.metis.dereference.OriginalEntity;
import eu.europeana.metis.dereference.Vocabulary;
import eu.europeana.metis.dereference.service.dao.CacheDao;
import eu.europeana.metis.dereference.service.dao.EntityDao;
import eu.europeana.metis.dereference.service.dao.VocabularyDao;
import eu.europeana.metis.mongo.EmbeddedLocalhostMongo;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;

/**
 * Created by ymamakis on 2/22/16.
 */
class MongoDereferencingManagementServiceTest {
    private MongoDereferencingManagementService service;
    private RedisProvider redisProvider;
    private Jedis jedis;
    private EntityDao entityDao;
    private EmbeddedLocalhostMongo embeddedLocalhostMongo = new EmbeddedLocalhostMongo();

    @BeforeEach
    void prepare() {
        embeddedLocalhostMongo.start();
        String mongoHost = embeddedLocalhostMongo.getMongoHost();
        int mongoPort = embeddedLocalhostMongo.getMongoPort();

        redisProvider = Mockito.mock(RedisProvider.class);
        jedis = Mockito.mock(Jedis.class);
        CacheDao cacheDao = new CacheDao(redisProvider);
        MongoClient mongo = new MongoClient(mongoHost, mongoPort);
        VocabularyDao vocDao = new VocabularyDao(mongo,"voctest");
        entityDao = new EntityDao(mongo,"voctest");
        service = new MongoDereferencingManagementService(vocDao, cacheDao,entityDao);

        Mockito.when(redisProvider.getJedis()).thenReturn(jedis);
    }

    @Test
    void testCreateRetrieveVocabulary() {
        Vocabulary voc = new Vocabulary();
        voc.setIterations(0);
        voc.setName("testName");
        voc.setRules(Collections.singleton("testRules"));
        voc.setType(ContextualClass.AGENT);
        voc.setTypeRules(Collections.singleton("testTypeRules"));
        voc.setUri("testURI");
        voc.setXslt("testXSLT");
        voc.setSuffix("testSuffix");
        service.saveVocabulary(voc);
        Vocabulary retVoc = service.findByName(voc.getName());
        assertEquals(voc.getName(),retVoc.getName());
        assertEquals(voc.getIterations(),retVoc.getIterations());
        assertEquals(voc.getRules(),retVoc.getRules());
        assertEquals(voc.getType(),retVoc.getType());
        assertEquals(voc.getTypeRules(),retVoc.getTypeRules());
        assertEquals(voc.getUri(),retVoc.getUri());
        assertEquals(voc.getXslt(),retVoc.getXslt());
        assertEquals(voc.getSuffix(),retVoc.getSuffix());
    }

    @Test
    void testCreateUpdateRetrieveVocabulary() {
        Mockito.doAnswer(invocation -> null).when(jedis).flushAll();
        Vocabulary voc = new Vocabulary();
        voc.setIterations(0);
        voc.setName("testName");
        voc.setRules(Collections.singleton("testRules"));
        voc.setType(ContextualClass.AGENT);
        voc.setTypeRules(Collections.singleton("testTypeRules"));
        voc.setUri("testURI");
        voc.setXslt("testXSLT");
        voc.setSuffix("testSuffix");
        service.saveVocabulary(voc);
        voc.setUri("testUri2");
        service.updateVocabulary(voc);
        Vocabulary retVoc = service.findByName(voc.getName());
        assertEquals(voc.getName(),retVoc.getName());
        assertEquals(voc.getIterations(),retVoc.getIterations());
        assertEquals(voc.getRules(),retVoc.getRules());
        assertEquals(voc.getType(),retVoc.getType());
        assertEquals(voc.getTypeRules(),retVoc.getTypeRules());
        assertEquals(voc.getUri(),retVoc.getUri());
        assertEquals(voc.getXslt(),retVoc.getXslt());
        assertEquals(voc.getSuffix(),retVoc.getSuffix());
    }

    @Test
    void testGetAllVocabularies() {
        Vocabulary voc = new Vocabulary();
        voc.setIterations(0);
        voc.setName("testName");
        voc.setRules(Collections.singleton("testRules"));
        voc.setType(ContextualClass.AGENT);
        voc.setTypeRules(Collections.singleton("testTypeRules"));
        voc.setUri("testURI");
        voc.setXslt("testXSLT");
        service.saveVocabulary(voc);
        List<Vocabulary> retVoc = service.getAllVocabularies();
        assertEquals(1,retVoc.size());
    }

    @Test
    void testDeleteVocabularies() {
        Mockito.doAnswer(invocation -> null).when(jedis).flushAll();
        Vocabulary voc = new Vocabulary();
        voc.setIterations(0);
        voc.setName("testName");
        voc.setRules(Collections.singleton("testRules"));
        voc.setType(ContextualClass.AGENT);
        voc.setTypeRules(Collections.singleton("testTypeRules"));
        voc.setUri("testURI");
        voc.setXslt("testXSLT");
        service.saveVocabulary(voc);
        List<Vocabulary> retVoc = service.getAllVocabularies();
        assertEquals(1,retVoc.size());
        service.deleteVocabulary(voc.getName());
        List<Vocabulary> retVoc2 = service.getAllVocabularies();
        assertEquals(0,retVoc2.size());
    }

    @Test
    void removeEntity() {
        OriginalEntity entity = new OriginalEntity();
        entity.setURI("testUri");
        entity.setXml("testXml");
        entityDao.save(entity);

        service.removeEntity(entity.getURI());

        assertNull(entityDao.get(entity.getURI()));
    }

    @Test
    void updateEntity() {
        OriginalEntity entity = new OriginalEntity();
        entity.setURI("testUri");
        entity.setXml("testXml");
        entityDao.save(entity);

        service.updateEntity(entity.getURI(),"testXml2");
        OriginalEntity entity1 = entityDao.get(entity.getURI());
        assertEquals("testXml2", entity1.getXml());
    }

    @AfterEach
    void destroy() {
        embeddedLocalhostMongo.stop();
    }
}
