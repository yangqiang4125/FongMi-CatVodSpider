package com.github.catvod.bean.yiso;

import com.github.catvod.bean.Vod;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Item {
    @SerializedName("data")
    private DataDTO data;

    public static Item objectFrom(String str) {
        try {
            return new Gson().fromJson(str, Item.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new Item();
        }
    }
    public DataDTO getData() {
        return data == null ? new DataDTO() : data;
    }

    public DataDTO getData(String pic) {
        return data == null ? new DataDTO(pic) : data;
    }

    public static class DataDTO {
        public String pic = "https://inews.gtimg.com/newsapp_bt/0/13263837859/1000";
        public DataDTO() {
        }
        public DataDTO(String pic) {
            this.pic = pic;
        }

        @SerializedName("list")
        private List<ListDTO> list;

        public List<Vod> getList() {
            List<Vod> items = new ArrayList<>();
            list = list == null ? Collections.emptyList() : list;
            for (ListDTO item : list) items.add(item.getVod(pic));
            return items;
        }

        public static class ListDTO {

            @SerializedName("url")
            private String url;
            @SerializedName("gmtCreate")
            private String gmtCreate;
            @SerializedName("fileInfos")
            private List<FileInfoDTO> fileInfos;

            public String getUrl() {
                return url;
            }

            public String getGmtCreate() {
                return gmtCreate;
            }

            public List<FileInfoDTO> getFileInfos() {
                return fileInfos;
            }

            public Vod getVod(String pic) {
                String id = getUrl();
                String name = getFileInfos().get(0).getFileName();
                String remark = getGmtCreate();
                return new Vod(id, name, pic, remark);
            }

            public static class FileInfoDTO {

                @SerializedName("fileName")
                private String fileName;

                public String getFileName() {
                    return fileName;
                }
            }
        }
    }
}
