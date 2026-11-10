package com.sakura.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakura.common.core.domain.entity.SysUser;
import com.sakura.common.utils.PublicUserIdGenerator;
import com.sakura.sakura.domain.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class PublicUserIdentityTest {
    @Test void defaultNicknameUsesPublicIdWithoutChangingRelationalId() {
        SysUser user = new SysUser(3L);
        user.setPublicId("3");
        new PublicUserIdGenerator(0, 0).initializeNewUser(user);
        assertEquals(3L, user.getUserId());
        assertTrue(user.getPublicId().matches("[1-9][0-9]{17,19}"));
        assertEquals("Sakura_" + user.getPublicId(), user.getNickName());
        assertTrue(user.getNickName().length() <= 30);
    }

    @Test void preservesCustomAndThirdPartyNicknames() {
        SysUser user = new SysUser();
        user.setNickName("我的微信昵称");
        new PublicUserIdGenerator(0, 0).initializeNewUser(user);
        assertEquals("我的微信昵称", user.getNickName());
    }

    @Test void singletonSequenceIsUniqueAcrossConcurrentCallersAndUsesConfiguredNode() {
        var first = new PublicUserIdGenerator(4, 3);
        var second = new PublicUserIdGenerator(4, 3);
        Set<String> ids = ConcurrentHashMap.newKeySet();
        IntStream.range(0, 2000).parallel().forEach(index -> {
            SysUser user = new SysUser();
            (index % 2 == 0 ? first : second).initializeNewUser(user);
            assertTrue(ids.add(user.getPublicId()));
            long id = Long.parseLong(user.getPublicId());
            assertEquals(4L, (id >> 12) & 31);
            assertEquals(3L, (id >> 17) & 31);
            assertTrue(Math.abs(((id >> 22) + 1288834974657L) - System.currentTimeMillis()) < 60_000);
        });
        assertEquals(2000, ids.size());
    }

    @Test void apiUsesStringWhileAdminEntityRetainsLongId() throws Exception {
        String publicId = "2123456789012345678";
        var mapper = new ObjectMapper();
        var publicJson = mapper.readTree(mapper.writeValueAsString(UserInfoVO.builder().userId(publicId).build()));
        assertTrue(publicJson.get("userId").isTextual());
        assertEquals(publicId, publicJson.get("userId").asText());
        assertFalse(publicJson.has("legacyUserId"));
        assertEquals(Long.class, SysUser.class.getMethod("getUserId").getReturnType());
    }

    @Test void rejectsInvalidOrReservedWorkerCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new PublicUserIdGenerator(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PublicUserIdGenerator(32, 0));
        assertThrows(IllegalArgumentException.class, () -> new PublicUserIdGenerator(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new PublicUserIdGenerator(31, 31));
    }
}
