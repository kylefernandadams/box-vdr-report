package com.box.platform.vdr.entity;

import java.util.Date;

/**
 * Created by kadams on 3/29/17.
 */
public class BoxExcelItem {

    public String dealRoom;
    public String id;
    public String path;
    public String name;
    public String type;
    public int itemCount;
    public Date createdAt;
    public String createdByLogin;
    public String createdByName;
    public Date modifiedAt;
    public String modifiedByLogin;
    public String modifiedByName;
    public Date originalContentCreationDate;
    public Date originalContentModifiedDate;

    public String getDealRoom() {
        return dealRoom;
    }

    public void setDealRoom(String dealRoom) {
        this.dealRoom = dealRoom;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedByLogin() {
        return createdByLogin;
    }

    public void setCreatedByLogin(String createdByLogin) {
        this.createdByLogin = createdByLogin;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public Date getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(Date modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public String getModifiedByLogin() {
        return modifiedByLogin;
    }

    public void setModifiedByLogin(String modifiedByLogin) {
        this.modifiedByLogin = modifiedByLogin;
    }

    public String getModifiedByName() {
        return modifiedByName;
    }

    public void setModifiedByName(String modifiedByName) {
        this.modifiedByName = modifiedByName;
    }

    public Date getOriginalContentCreationDate() {
        return originalContentCreationDate;
    }

    public void setOriginalContentCreationDate(Date originalContentCreationDate) {
        this.originalContentCreationDate = originalContentCreationDate;
    }

    public Date getOriginalContentModifiedDate() {
        return originalContentModifiedDate;
    }

    public void setOriginalContentModifiedDate(Date originalContentModifiedDate) {
        this.originalContentModifiedDate = originalContentModifiedDate;
    }
}
