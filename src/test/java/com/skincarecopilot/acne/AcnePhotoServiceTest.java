package com.skincarecopilot.acne;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincarecopilot.knowledge.*;
import com.skincarecopilot.profile.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AcnePhotoServiceTest {
    @Test void sendsImageAndConfirmedContextAndReturnsActualSources() throws Exception {
        var mapper=new ObjectMapper(); var captured=new ArrayList<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.add(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            var body="{\"choices\":[{\"message\":{\"content\":\"可能有粉刺样表现，建议温和洁面 [1]。\"}}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try {
            var profiles=mock(ProfileService.class); when(profiles.find("demo-user")).thenReturn(Optional.of(new Profile("demo-user","干皮",List.of("香精"))));
            var source=new KnowledgeEntry("aad-care","AAD","痤疮护理","https://www.aad.org/example","清洁","2026-01-01","原文","温和清洁。");
            var chunk=new KnowledgeChunk("aad-care:0","aad-care",0,5,"温和清洁。",source);
            var knowledge=mock(KnowledgeService.class); when(knowledge.search(anyString(),eq(5))).thenReturn(new KnowledgeService.SearchResult("q","model",.75,List.of(new KnowledgeService.SearchHit(.9,chunk))));
            var hormoneSource=new KnowledgeEntry("book-hormone","Book","内分泌教材","book:example","皮脂","2026-01-01","原文","皮脂与激素有关。");
            var hormoneChunk=new KnowledgeChunk("book-hormone:0","book-hormone",0,8,"皮脂与激素有关。",hormoneSource);
            when(knowledge.searchEndocrinology(anyString(),eq(2))).thenReturn(List.of(new KnowledgeService.SearchHit(.9,hormoneChunk)));
            var chat=mock(com.skincarecopilot.chat.ChatService.class);
            var service=new AcnePhotoService(profiles,knowledge,new KnowledgeRelevanceFilter(),chat,mapper,HttpClient.newHttpClient(),"test-key","http://127.0.0.1:"+server.getAddress().getPort()+"/v1","vision-test");
            var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream(); ImageIO.write(image,"png",bytes);
            var result=service.analyze("demo-user","session-1","经期前更明显，怎么看",bytes.toByteArray());
            assertTrue(result.answer().contains("粉刺")); assertEquals("aad-care",result.sources().get(0).id());
            assertTrue(result.sources().stream().anyMatch(s -> s.id().equals("book-hormone")));
            assertEquals(2, captured.size());
            var sent=mapper.readTree(captured.get(0)); assertEquals("vision-test",sent.path("model").asText());
            var content=sent.path("messages").get(0).path("content");
            assertTrue(mapper.readTree(captured.get(1)).path("messages").get(0).path("content").asText().contains("香精"));
            verify(knowledge).search(contains("可能有粉刺样表现"), eq(5));
            assertTrue(content.get(1).path("image_url").path("url").asText().startsWith("data:image/png;base64,"));
            verify(profiles,never()).saveConfirmed(anyString(),any());
            verify(chat).rememberPhotoTurn(eq("demo-user"),eq("session-1"),eq("经期前更明显，怎么看"),anyString(),anyString());
            verify(knowledge).searchEndocrinology(contains("经期"),eq(2));
        } finally {server.stop(0);}
    }
    @Test void rejectsNonImagesAndOversize() {
        assertThrows(IllegalArgumentException.class,()->AcnePhotoService.validateImage("not an image".getBytes()));
        assertThrows(IllegalArgumentException.class,()->AcnePhotoService.validateImage(new byte[5*1024*1024+1]));
    }
}
