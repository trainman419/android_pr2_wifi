To anyone who has the misfortune of encountering this project in the future,

This project was built during a dark time when the ROS android tools weren't
working properly; as a result, the makefile simply builds all of the dependent 
jar files and copies them into android/libs. The project itself is an eclipse
project, and assumes that you've imported the appmanandroid project into
eclipse. It may also be necessary to copy a few jar files from appmanandroid
into the libs directory.

The code itself shouldn't have any of these stupid build dependencies, so maybe
at some point I'll come back and fix it.

Good Luck!
-Austin
