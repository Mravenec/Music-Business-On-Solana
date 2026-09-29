package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import java.time.LocalDateTime;

/** Builds the JOOQ POJO request bodies the authoring endpoints bind. */
final class CourseInputs {

  private CourseInputs() {}

  static Course course(String title) {
    Course c = new Course();
    c.setTitle(title);
    return c;
  }

  static Course course(String title, Long instrumentId, Integer minLevel) {
    Course c = course(title);
    c.setInstrumentId(instrumentId);
    c.setMinLevel(minLevel == null ? null : minLevel.byteValue());
    return c;
  }

  static Course sortOrder(int position) {
    Course c = new Course();
    c.setSortOrder(position);
    return c;
  }

  static Course reviewNote(String note) {
    Course c = new Course();
    c.setReviewNote(note);
    return c;
  }

  static CourseSection section(String title) {
    CourseSection s = new CourseSection();
    s.setTitle(title);
    return s;
  }

  static Lesson lesson(String title, String videoUrl) {
    Lesson l = new Lesson();
    l.setTitle(title);
    l.setVideoUrl(videoUrl);
    return l;
  }

  static Lesson withActive(Lesson stored, boolean active) {
    Lesson l = new Lesson(stored);
    l.setIsActive(active ? (byte) 1 : (byte) 0);
    return l;
  }

  static CourseAccessGrant grant(String wallet, Long courseId, String note) {
    CourseAccessGrant g = new CourseAccessGrant();
    g.setWalletPubkey(wallet);
    g.setCourseId(courseId);
    g.setNote(note);
    return g;
  }

  static CourseAccessGrant grantUntil(String wallet, LocalDateTime expiresAt) {
    CourseAccessGrant g = grant(wallet, null, null);
    g.setExpiresAt(expiresAt);
    return g;
  }
}
