package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.PlayerRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryCacheTest {
    @Test
    void countsByTab() {
        UUID me = UUID.randomUUID();
        List<MailRecord> active = List.of(
                MailRecord.builder().kind(MailKind.PARCEL).receiver(me).state(MailState.COD).codPrice(5).build(),
                MailRecord.builder().kind(MailKind.PARCEL).receiver(me).readAt(5).build(),
                MailRecord.builder().kind(MailKind.DAILY).receiver(me).build(),
                MailRecord.builder().kind(MailKind.MESSAGE).receiver(me).readAt(3).build(),
                MailRecord.builder().kind(MailKind.RECEIPT).receiver(me).build());
        PlayerRecord record = new PlayerRecord(me, "Me", 1, 2, true, 100, 4, 9);
        SummaryCache.Summary summary = SummaryCache.compute(active, 2, record);
        assertEquals(2, summary.parcels());
        assertEquals(1, summary.cod());
        assertEquals(1, summary.rewards());
        assertEquals(4, summary.inbox(), "2 mails + 2 campaigns");
        assertEquals(5, summary.unread(), "3 unread mails + 2 campaigns");
        assertEquals(7, summary.waiting());
        assertEquals(4, summary.streak());
        assertEquals(100, summary.dailyDay());
    }
}
