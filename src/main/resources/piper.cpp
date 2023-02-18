#include<iostream>
#include<sys/wait.h>
#include<unistd.h>
#include<string.h>
#include<stdio.h>
#include<string>
#include<vector>
using namespace std;
struct pipes {
    int pipeIn[2];
    int pipeOut[2];
};
void error_message (string s, int code) {
    cerr << s << endl ;
    exit(code);
}
void communication_problem (int signal) {
    error_message("Violation of the protocol for communication!",1);
}
pair <int, int> make_process (char* grader) {
    pipes p;
    if ((pipe(p.pipeIn))||(pipe(p.pipeOut))) error_message("Pipe failed",-2);
    int pid=fork();
    if (pid<0) error_message("Fork failed",-3);
    else if (pid==0) {
        close(p.pipeOut[1]); close(p.pipeIn[0]);
        dup2(p.pipeOut[0],0);
        dup2(p.pipeIn[1],1);
        execl(grader,grader,NULL);
        error_message("Exec failed",-4);
    }
    else {
        close(p.pipeOut[0]); close(p.pipeIn[1]);
        return {p.pipeIn[0], p.pipeOut[1]};
    }
}
int main (int argc, char* argv[]) {
    if (argc<4) error_message("Arguments: number_of_grader_processes name_of_manager_program name_of_grader_program",-1);
    int processes=atoi(argv[1]);
    if ((argc>4)&&(argc!=3+processes)) error_message("If there is more than one name of grader program, the count of the names should match the number of processes",-1);

    signal(SIGPIPE,communication_problem);
    vector <int> fds;
    for (int i=0; i<processes; i++) {
        char* grader;
        if (argc==4) grader=argv[3];
        else grader=argv[3+i];
        pair <int, int> res=make_process(grader);
        fds.push_back(res.first);
        fds.push_back(res.second);
    }

    int pid=fork();
    if (pid<0) error_message("Fork failed",-3);
    else if (pid==0) {
        char* manager=argv[2];
        char** args=new char*[fds.size()+2];
        args[0]=manager;
        for (int i=0; i<fds.size(); i++) {
            int cnt=0,tmp=fds[i];
            for (;;) {
                tmp/=10;
                cnt++;
                if (tmp==0) break;
            }
            args[1+i]=new char[cnt+1];
            sprintf(args[1+i],"%d",fds[i]);
        }
        args[fds.size()+1]=NULL;
        signal(SIGPIPE,SIG_IGN);
        execv(manager,args);
        error_message("Exec failed",-4);
    }
    for (auto fd : fds) {
        close(fd);
    }

    int status;
    while ((pid=wait(&status))>0) {
        if (WIFEXITED(status)) {
            if (WEXITSTATUS(status)) exit(WEXITSTATUS(status));
        }
        else if (WIFSIGNALED(status)) {
            kill(getpid(),WTERMSIG(status));
        }
        else if (WIFSTOPPED(status)) {
            kill(getpid(),WSTOPSIG(status));
        }
        else {
            error_message("Unknown status of child process - "+to_string(status),-5);
        }
    }
    return 0;
}
