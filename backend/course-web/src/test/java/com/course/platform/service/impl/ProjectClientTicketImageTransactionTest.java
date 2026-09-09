package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.course.platform.infra.projectclient.ProjectTicketImageCodecTest.data;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.projectclient.*;
import com.course.platform.domain.projectclient.ProjectClientTicketTypes.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.StatusForm;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.infra.projectclient.ProjectTicketImageCodec;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

class ProjectClientTicketImageTransactionTest extends ProjectClientTestSupport {
    ProjectClientTicketServiceImpl tickets;
    String clientId;
    @BeforeEach void setupImages() {
        tickets=new ProjectClientTicketServiceImpl(sql.getMapper(ProjectClientTicketMapper.class),
                sql.getMapper(ProjectClientTicketImageMapper.class),new ProjectTicketImageCodec(),
                sql.getMapper(ProjectClientTicketReplyMapper.class),sql.getMapper(ProjectClientTicketCommandMapper.class),
                clients,users,keys,validation.getValidator(),limiter,new RateLimitProperties(),new DataSourceTransactionManager(jdbc.getDataSource()));
        ReflectionTestUtils.setField(tickets,"enabled",true);ReflectionTestUtils.setField(tickets,"cryptoSecret","fixture-image-key");
        clientId=open("0").id();
    }
    CreateForm createForm(String data) {return new CreateForm(UUID.randomUUID().toString(),clientId,"BUG","图片说明","这是客户问题描述",BigDecimal.ZERO,true,data);}
    TicketView create(String data) {var r=tickets.create(keys.web(),createForm(data));return tickets.ticket(keys.web(),r.ticketId());}
    long count(String t) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+t,Long.class);}
    @Test void createStoresOnlyEncryptedSanitizedImageAndOpaquePublicIdentifier() throws Exception {
        var input=data("png",0x234567);var t=create(input);assertNotNull(t.imageId());
        var row=sql.getMapper(ProjectClientTicketImageMapper.class).selectById(t.imageId());assertNull(row.getContentEncrypted());
        String cipher=sql.getMapper(ProjectClientTicketImageMapper.class).content(t.imageId(),t.id());assertTrue(SecretCrypto.isEncrypted(cipher));assertFalse(cipher.contains(input));
        assertArrayEquals(new ProjectTicketImageCodec().normalize(input).png(),tickets.image(keys.web(),t.imageId()));
        assertFalse(t.toString().contains("base64"));assertEquals(0,ledgerCount());wallet("100");
    }
    @Test void supportsImageOnlyReplyInSameTransactionWithVersionAndReceipt() throws Exception {
        var t=create(null);var f=new ReplyForm(UUID.randomUUID().toString(),0L,null,true,data("jpeg",0x123456));
        tickets.reply(keys.web(),t.id(),f);tickets.reply(keys.web(),t.id(),f);
        var replies=tickets.replies(keys.web(),t.id(),1,20);assertEquals(1,replies.getTotal());
        assertEquals("",replies.getRecords().get(0).content());assertNotNull(replies.getRecords().get(0).imageId());
        assertEquals(1,count("project_client_ticket_image"));assertEquals(1,tickets.ticket(keys.web(),t.id()).version());
    }
    @Test void exactCustomerAndOwnerScopesApplyBeforeDecryptingImage() throws Exception {
        var t=create(data("png",1));var other=open("0").id();var otherCaller=keys.authenticate(issue(other,"SUPPORT").secret());
        assertThrows(BusinessException.class,()->tickets.image(otherCaller,t.imageId()));
        var own=keys.authenticate(issue(clientId,"READ_ONLY").secret());assertTrue(tickets.image(own,t.imageId()).length>0);
        auth(8);assertThrows(BusinessException.class,()->tickets.image(keys.web(),t.imageId()));
    }
    @Test void suspendedAndRotatedKeysLoseImageAccessButOwnerKeepsHistoricalAccess() throws Exception {
        var t=create(data("png",2));var caller=keys.authenticate(issue(clientId,"SUPPORT").secret());
        issue(clientId,"READ_ONLY");assertThrows(BusinessException.class,()->tickets.image(caller,t.imageId()));
        var active=keys.authenticate(issue(clientId,"READ_ONLY").secret());
        service.status(keys.web(),clientId,new StatusForm(service.client(keys.web(),clientId).version(),"SUSPENDED",true));
        assertThrows(BusinessException.class,()->tickets.image(active,t.imageId()));assertTrue(tickets.image(keys.web(),t.imageId()).length>0);
    }
    @Test void imageInsertFailureRollsBackNewTicketAndRequestReceipt() throws Exception {
        var broken=spy(sql.getMapper(ProjectClientTicketImageMapper.class));doReturn(0).when(broken).insert(any(ProjectClientTicketImage.class));
        ReflectionTestUtils.setField(tickets,"images",broken);var f=createForm(data("png",0));
        assertThrows(BusinessException.class,()->tickets.create(keys.web(),f));assertEquals(0,count("project_client_ticket"));assertEquals(0,count("project_client_ticket_image"));assertNull(tickets.byRequest(keys.web(),f.requestId()));
    }
    @Test void receiptFailureRollsBackImageReplyAndVersionTogether() throws Exception {
        var t=create(null);var broken=spy(sql.getMapper(ProjectClientTicketCommandMapper.class));doReturn(0).when(broken).insert(any(ProjectClientTicketCommand.class));ReflectionTestUtils.setField(tickets,"commands",broken);
        var f=new ReplyForm(UUID.randomUUID().toString(),0L,"",true,data("png",0));
        assertThrows(BusinessException.class,()->tickets.reply(keys.web(),t.id(),f));assertEquals(0,count("project_client_ticket_image"));assertEquals(0,count("project_client_ticket_reply"));assertEquals(0,tickets.ticket(keys.web(),t.id()).version());
    }
    @Test void changedImageCannotReuseOriginalRequestId() throws Exception {
        var f=createForm(data("png",1));tickets.create(keys.web(),f);
        var changed=new CreateForm(f.requestId(),f.clientId(),f.kind(),f.title(),f.description(),f.requestedAmount(),true,data("png",2));
        assertThrows(BusinessException.class,()->tickets.create(keys.web(),changed));assertEquals(1,count("project_client_ticket_image"));
    }
    @Test void concurrentSameImageSubmitCommitsOneTicketAndOneImage() throws Exception {
        var caller=keys.authenticate(issue("OWNER","MANAGE").secret());var f=createForm(data("png",0));
        var a=threads.submit(()->tickets.create(caller,f));var b=threads.submit(()->tickets.create(caller,f));
        assertEquals(a.get(10,TimeUnit.SECONDS).ticketId(),b.get(10,TimeUnit.SECONDS).ticketId());assertEquals(1,count("project_client_ticket_image"));
    }
    @Test void corruptEncryptedImageFailsClosedWithoutReturningCiphertext() throws Exception {
        var t=create(data("png",0));jdbc.update("UPDATE project_client_ticket_image SET content_encrypted='private-corrupt-cipher' WHERE id=?",t.imageId());
        var error=assertThrows(BusinessException.class,()->tickets.image(keys.web(),t.imageId()));assertFalse(error.getMessage().contains("private-corrupt"));
    }
    @Test void disabledFeatureAllowsOwnerImageReadButNoUploads() throws Exception {
        var t=create(data("png",0));ReflectionTestUtils.setField(tickets,"enabled",false);
        assertTrue(tickets.image(keys.web(),t.imageId()).length>0);
        var f=createForm(data("png",0));assertThrows(BusinessException.class,()->tickets.create(keys.web(),f));
    }
    @Test void textOnlyReceiptsRemainReplayCompatibleAndBlankReplyIsNotSilentlyAccepted() {
        var f=createForm(null);var r=tickets.create(keys.web(),f);assertEquals(r.ticketId(),tickets.create(keys.web(),f).ticketId());
        assertThrows(BusinessException.class,()->tickets.reply(keys.web(),r.ticketId(),new ReplyForm(UUID.randomUUID().toString(),0L,"",true,null)));
    }
    @Test void invalidImageOrMissingCryptoCannotLeaveTicketBehind() {
        assertThrows(BusinessException.class,()->tickets.create(keys.web(),createForm("https://unused.example/image.png")));
        ReflectionTestUtils.setField(tickets,"cryptoSecret","");
        assertThrows(BusinessException.class,()->tickets.create(keys.web(),createForm(data("png",0))));assertEquals(0,count("project_client_ticket"));
    }
}
