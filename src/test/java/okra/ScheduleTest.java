package okra;

import okra.base.Okra;
import okra.exception.OkraRuntimeException;
import okra.model.DefaultOkraItem;
import org.junit.Test;

public class ScheduleTest extends OkraBaseContainerTest {

    @Test(expected = OkraRuntimeException.class)
    public void shouldNotScheduleIfRunDateIsNull() {
        DefaultOkraItem item = new DefaultOkraItem();
        scheduleWithOkra(okraSimpleMongo32, item);
        item = new DefaultOkraItem();
        scheduleWithOkra(okraSimpleMongo34, item);
    }

    @Test(expected = OkraRuntimeException.class)
    public void shouldNotScheduleIfIdIsNotNull() {
        DefaultOkraItem item = new DefaultOkraItem();
        item.setId("123456");
        scheduleWithOkra(okraSimpleMongo32, item);

        item = new DefaultOkraItem();
        item.setId("123456");
        scheduleWithOkra(okraSimpleMongo34, item);
    }

    private void scheduleWithOkra(Okra<DefaultOkraItem> okra, DefaultOkraItem item) {
        okra.schedule(item);
    }

}
