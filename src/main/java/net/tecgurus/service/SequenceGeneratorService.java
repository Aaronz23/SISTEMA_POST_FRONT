package net.tecgurus.service;

import net.tecgurus.model.DbSequence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class SequenceGeneratorService {

    @Autowired
    private MongoOperations mongoOps;
    public long next(String name) {
        Query q = new Query(Criteria.where("_id").is(name));
        Update u = new Update().inc("seq", 1);
        FindAndModifyOptions opt = FindAndModifyOptions.options().returnNew(true).upsert(true);
        DbSequence res = mongoOps.findAndModify(q, u, opt, DbSequence.class, "counters");
        return (res != null) ? res.getSeq() : 1L;
    }
}
