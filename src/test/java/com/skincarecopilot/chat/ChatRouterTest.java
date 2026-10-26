package com.skincarecopilot.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatRouterTest {
    private final ChatRouter router = new ChatRouter();

    @Test
    void routesAllFourBranchesAndFactualShortQuestions() {
        assertEquals(ChatRoute.SMALL_TALK, router.route("你好，今天怎么样"));
        assertEquals(ChatRoute.PROFILE, router.route("我的肤质是什么"));
        assertEquals(ChatRoute.KNOWLEDGE, router.route("烟酰胺能祛痘吗"));
        assertEquals(ChatRoute.KNOWLEDGE, router.route("玻尿酸有用吗"));
        assertEquals(ChatRoute.KNOWLEDGE, router.route("低升糖饮食与痤疮有关系吗"));
        assertEquals(ChatRoute.COMBINED, router.route("我的肤质适合什么护肤方法"));
        assertEquals(ChatRoute.COMBINED, router.route("我有泛红，结合我的画像有什么资料"));
    }
}
