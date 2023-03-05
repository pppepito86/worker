#include<iostream>
#include<sys/wait.h>
#include<unistd.h>
#include<string.h>
#include<stdio.h>
#include<string>
#include<vector>
using namespace std;
void error_message (string s, int code) {
    cout << 0 << endl ;
    cout << s << endl ;
    exit(code);
}

struct pipes {
    int pipeIn[2];
    int pipeOut[2];
};
vector <int> fds;
vector <int> children;
int group=-1;
pair <int, int> make_process (char* grader) {
    pipes p;
    if ((pipe(p.pipeIn))||(pipe(p.pipeOut))) error_message("Pipe failed",-2);
    int pid=fork();
    if (pid<0) error_message("Fork failed",-3);
    else if (pid==0) {
        for (auto fd : fds) {
            close(fd);
        }
        close(p.pipeOut[1]); close(p.pipeIn[0]);
        dup2(p.pipeOut[0],0);
        dup2(p.pipeIn[1],1);
        signal(SIGPIPE,SIG_IGN);
        execl(grader,grader,NULL);
        error_message("Exec failed",-4);
    }
    else {
        children.push_back(pid);
        if (group==-1) group=pid;
        else setpgid(pid,group);
        close(p.pipeOut[0]); close(p.pipeIn[1]);
        return {p.pipeIn[0], p.pipeOut[1]};
    }
}
int count_digs (int num) {
    int cnt=0;
    for (;;) {
        num/=10;
        cnt++;
        if (num==0) break;
    }
    return cnt;
}
int main (int argc, char* argv[]) {
    if (argc!=4) error_message("Arguments: number_of_grader_processes name_of_manager_program name_of_grader_program",-1);
    int processes=atoi(argv[1]);
    signal(SIGPIPE,[] (int signal) {
        error_message("Pipe error - violation of the protocol for communication!",0);
    });
    for (int i=0; i<processes; i++) {
        char* grader;
        grader=argv[3];
        pair <int, int> res=make_process(grader);
        fds.push_back(res.first);
        fds.push_back(res.second);
    }

    int manager=fork();
    if (manager<0) error_message("Fork failed",-3);
    else if (manager==0) {
        char* manager=argv[2];
        char** args=new char*[1+fds.size()+children.size()+1];
        int ind=0;
        args[ind++]=manager;
        for (auto fd : fds) {
            args[ind]=new char[count_digs(fd)+1];
            sprintf(args[ind++],"%d",fd);
        }
        for (auto child : children) {
            args[ind]=new char[count_digs(child)+1];
            sprintf(args[ind++],"%d",child);
        }
        args[ind++]=NULL;
        execv(manager,args);
        error_message("Exec failed",-4);
    }
    for (auto fd : fds) {
        close(fd);
    }

    int pid,status;
    while ((pid=wait(&status))>0) {
        if (WIFEXITED(status)) {
            if (WEXITSTATUS(status)) exit(WEXITSTATUS(status));
            else if (pid==manager) {
                kill(-group,SIGTERM);
                exit(0);
            }
        }
        else if (WIFSIGNALED(status)) kill(getpid(),WTERMSIG(status));
        else if (WIFSTOPPED(status)) kill(getpid(),WSTOPSIG(status));
        else error_message("Unknown status of child process - "+to_string(status),-5);
    }
    return 0;
}
