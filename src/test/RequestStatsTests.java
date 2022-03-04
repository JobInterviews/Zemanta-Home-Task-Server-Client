package test;

import com.zemanta.task.server.Server;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestStatsTests {

    @Test
    public void  givenRequestStats_whenAllowedRequestPerTimeFrameExceeded_thenBlocked(){
        Server.RequestStats rs =  new Server.RequestStats(1,  System.currentTimeMillis(), 5);

       assertFalse(rs.isValid());
    }

    @Test
    public void  givenRequestStats_whenAllowedRequestPerTimeFrameNOtExceeded_thenAllowed(){
        Server.RequestStats rs =  new Server.RequestStats(1,  System.currentTimeMillis(), 4);

        assertTrue(rs.isValid());
    }
}
