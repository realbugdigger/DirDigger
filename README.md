# DirDigger

![DirDigger](https://raw.githubusercontent.com/realbugdigger/DirDigger/main/DD_MagGlass.svg)

DirDigger is tool used for directory digging/busting and it provides few more features than its alternatives.
It can be used for scanning web apps that have bug bounty programs as well as for ctfs.
DirDigger was started as portfolio project as Burp extension but after were added features 
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

Interesting things that can be added:
- Contexts (if domain has subdomains and you want to scan them at the same time, by changing context you can see progress and trees for preticular hostname)
    - In here maybe some cross context communication can exist (e.g. when one hostname redirects to another)
- Pass selected urls, or 200 urls, to site map (one-way sync)
    - there can exist two-way sync also -> to initialize tree in DirDigger extension based on site map and start working on that
- add option to create contexts based on target (Burp targets)
- create cli version and modularize the project

***

For those who don't want to use this as burp extension but as java app follow these steps
1. clone git repository
2. git checkout localDevelopment
3. mvn clean install
4. jar dirdigger

***

