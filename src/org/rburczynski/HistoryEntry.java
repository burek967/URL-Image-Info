package org.rburczynski;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@SuppressWarnings("unused")
@Entity
class HistoryEntry implements Serializable{

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public String toString(){
        return "Entry URL: " + this.url;
    }

    @GeneratedValue
    @Id
    @Column()
    private long id = 0;
    @Column()
    private String url;
    @Column()
    private LocalDateTime date;
}
