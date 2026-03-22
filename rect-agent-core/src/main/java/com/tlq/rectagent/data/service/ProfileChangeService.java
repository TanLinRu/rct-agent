package com.tlq.rectagent.data.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tlq.rectagent.data.entity.ProfileChange;
import com.tlq.rectagent.data.mapper.ProfileChangeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileChangeService extends ServiceImpl<ProfileChangeMapper, ProfileChange> {

    public ProfileChange recordChange(String userId, String fieldName, 
                                      String oldValue, String newValue, String reasoning) {
        ProfileChange change = new ProfileChange();
        change.setUserId(userId);
        change.setFieldName(fieldName);
        change.setOldValue(oldValue);
        change.setNewValue(newValue);
        change.setReasoning(reasoning);
        this.save(change);
        log.info("Recorded profile change for user: {}, field: {}", userId, fieldName);
        return change;
    }

    public List<ProfileChange> getChangesByUserId(String userId) {
        List<ProfileChange> changes = this.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProfileChange>()
                .eq(ProfileChange::getUserId, userId)
                .orderByDesc(ProfileChange::getCreatedAt));
        log.debug("查询用户画像变更: userId={}, 变更数量={}", userId, changes.size());
        return changes;
    }
}
