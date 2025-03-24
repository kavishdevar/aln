#include "blescanner.h"
#include <QApplication>

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);
    BleScanner scanner;
    scanner.show();
    return app.exec();
}