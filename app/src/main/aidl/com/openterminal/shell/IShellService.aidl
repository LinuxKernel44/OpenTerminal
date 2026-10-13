package com.openterminal.shell;

import android.os.ParcelFileDescriptor;

interface IShellService {
    void destroy() = 16777114;

    /** Starts a shell on a new PTY and returns its session id. */
    int openSession(int rows, int cols, String cwd) = 1;
    ParcelFileDescriptor getMasterFd(int id) = 2;
    int getPid(int id) = 3;
    void resize(int id, int rows, int cols) = 4;
    /** Blocks until the shell exits and returns its exit status. */
    int waitFor(int id) = 5;
    void closeSession(int id) = 6;
    int getUid() = 7;
}
