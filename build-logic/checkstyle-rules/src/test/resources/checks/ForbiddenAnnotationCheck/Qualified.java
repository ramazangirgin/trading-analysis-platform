package demo;

import com.example.Marked;

@Marked // violation
class Imported {}

@com.example.Marked // violation
class WrittenQualified {}

@org.other.Marked
class OtherQualified {}
