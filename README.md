# DirDigger

![DirDigger](https://raw.githubusercontent.com/realbugdigger/DirDigger/main/DD_MagGlass.svg)

DirDigger is an efficient directory digging and busting tool that offers a range of features surpassing those of its alternatives. It serves a dual purpose, being an invaluable asset for both CTF enthusiasts and individuals engaged in the scanning of web app directories and API URLs encompassing bug bounty programs.

Originally conceived as a portfolio project to create a Burp extension similar to gobuster/dirbuster, DirDigger experienced the creation and implementation of novel ideas and features, transforming it into a fully comprehensive directory digging tool.


DirDigger current features:
- Burp extension as well as java application
- Recursive (directory depth and thread number can be set)
- Proxy support
- Filtered response codes
- Follow redirects
- File extensions
- Multiple file loading for keywords list
- Tree representation for existing urls
- See urls that are grouped by redirect in a Redirect Tree (if redirects were followed)
- Rate limiter detection (if dettected scanning will be slowed down and addapted)
- Ignoring volatile params
- Stoping and Continuing execution
- Saving progress to a file and option to load a file to continue execution or see results (usefull when working in team)

To-do list for future enhancements:
- Contexts (if domain has subdomains and you want to scan them at the same time, by changing context you can see progress and trees for preticular hostname)
    - In here maybe some cross context communication can exist (e.g. when one hostname redirects to another)
- Pass selected urls, or 200 urls, to site map (one-way sync)
    - there can exist two-way sync also -> to initialize tree in DirDigger extension based on site map and start working on that
- add option to create contexts based on target (Burp targets)
- create cli version and modularize the project
- save threads and application state in some PostgreSQL db (can be useful for teams that are performing application security testing)

***

For those who don't want to use this as burp extension but as java app follow these steps
1. clone git repository
2. cd DirDigger
3. git checkout localDevelopment
4. mvn clean install
5. java -jar target/dirdigger-1.0-SNAPSHOT.jar

***

If using as burp extension follow these steps
1. clone git repository
2. cd DirDigger
3. mvn clean install
4. add `dirdigger-1.0-SNAPSHOT.jar` not `original-dirdigger-1.0-SNAPSHOT.jar` as original does not contain dependencies

***

## Licence
This project is released under the [MIT License](https://github.com/realbugdigger/DirDigger/blob/main/LICENSE).

