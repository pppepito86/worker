#include<iostream>
#include<unordered_set>
#include<sys/wait.h>
#include<sys/sem.h>
#include<unistd.h>
#include<string.h>
#include<errno.h>
#include<stdio.h>
#include<string>
#include<vector>
using namespace std;
void error_message (string s, int code) {
    cout << 0 << endl ;
    cout << "Errno: " << errno << ", message: " << s << endl ;
    exit(code);
}

struct pipes {
    int pipeIn[2];
    int pipeOut[2];
};
vector <int> fds;
vector <int> children;
int group=-1,sem_id;
sembuf s;
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
        if (group==-1) setpgid(0,0);
        else setpgid(0,group);
        s.sem_op=1;
        if (semop(sem_id,&s,1)==-1) error_message("Semop failed",-5);
        execl(grader,grader,NULL);
        error_message("Exec failed",-4);
    }
    else {
        if (group==-1) group=pid;
        children.push_back(pid);
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
    sem_id=semget(IPC_PRIVATE,1,0600|IPC_CREAT);
    if (sem_id<0) error_message("Semget failed",-5);
    s.sem_flg=0;
    s.sem_num=0;
    for (int i=0; i<processes; i++) {
        char* grader;
        grader=argv[3];
        pair <int, int> res=make_process(grader);
        s.sem_op=-1;
        if (semop(sem_id,&s,1)==-1) error_message("Semop failed",-5);
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
                if (kill(-group,0)==0) kill(-group,SIGTERM);
                exit(0);
            }
        }
        else if (WIFSIGNALED(status)) kill(getpid(),WTERMSIG(status));
        else if (WIFSTOPPED(status)) kill(getpid(),WSTOPSIG(status));
        else error_message("Unknown status of child process - "+to_string(status),-6);
    }
    return 0;
}
